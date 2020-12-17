package server;

import config.HostConfig;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.websocket.WsContext;
import matching.FuzzySearch;
import matching.MatchingVisitor;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.ScoreDoc;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http2.HTTP2Cipher;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import planning.viz.SimpleVizPlanner;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static matching.FuzzySearch.searcher;

public class LuceneServlet {
    private static Map<WsContext, String> userUsernameMap = new ConcurrentHashMap<>();
    private static int nextUserNumber = 1;
    private static Connection connection;

    private static Server createHttp2Server() {
        Server server = new Server();

        ServerConnector connector = new ServerConnector(server);
        connector.setPort(80);
        server.addConnector(connector);

        // HTTP Configuration
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSendServerVersion(false);
        httpConfig.setSecureScheme("https");
        httpConfig.setSecurePort(HostConfig.SERVER_PORT);

        // SSL Context Factory for HTTPS and HTTP/2
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStorePath(LuceneServlet.class.getResource("/keystore.jks").toExternalForm()); // replace with your real keystore
        sslContextFactory.setKeyStorePassword("password"); // replace with your real password
        sslContextFactory.setCipherComparator(HTTP2Cipher.COMPARATOR);
        sslContextFactory.setProvider("Conscrypt");

        // HTTPS Configuration
        HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
        httpsConfig.addCustomizer(new SecureRequestCustomizer());

        // HTTP/2 Connection Factory
        HTTP2ServerConnectionFactory h2 = new HTTP2ServerConnectionFactory(httpsConfig);
        ALPNServerConnectionFactory alpn = new ALPNServerConnectionFactory();
        alpn.setDefaultProtocol("h2");

        // SSL Connection Factory
        SslConnectionFactory ssl = new SslConnectionFactory(sslContextFactory, alpn.getProtocol());

        // HTTP/2 Connector
        ServerConnector http2Connector = new ServerConnector(server, ssl, alpn, h2, new HttpConnectionFactory(httpsConfig));
        http2Connector.setPort(HostConfig.SERVER_PORT);
        server.addConnector(http2Connector);

        return server;
    }
    

    public static void main(String[] args) throws SQLException {
        Server server = createHttp2Server();
        Javalin app = Javalin.create(config -> {
            config.server(() -> server);
            config.addStaticFiles("./html", Location.EXTERNAL);
            config.addSinglePageRoot("/", "./html/sqlova.html", Location.EXTERNAL);
        }).start(HostConfig.SERVER_PORT);

        String url = HostConfig.DB_HOST;
        Properties props = new Properties();
        props.setProperty("user", "monetdb");
        props.setProperty("password", "monetdb");
        connection = DriverManager.getConnection(url, props);

//        app.post("/", ctx -> {
//            // some code
//            String message = ctx.message();
//            ctx.result();
//        });

        app.ws("/lucene", ws -> {
            ws.onConnect(ctx -> {
                String username = "User" + nextUserNumber++;
                userUsernameMap.put(ctx, username);
            });
            ws.onClose(ctx -> {
                userUsernameMap.remove(ctx);
            });
            ws.onMessage(ctx -> {
                String message = ctx.message();
                String[] query_list = message.split(";");
                List<String> commands = new ArrayList<>(6);
                commands.add("curl");
                commands.add("-F");
                commands.add("t=" + query_list[0]);
                commands.add("-F");
                commands.add("q=" + query_list[1]);
                commands.add(HostConfig.MODEL_HOST);

                ProcessBuilder processBuilder = new ProcessBuilder(commands);
                Process process = processBuilder.start();

                String result = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))
                        .lines()
                        .collect(Collectors.joining("\n"));
                System.out.println(result);
                JSONObject jsonObject = new JSONObject(result);
                searchResults(ctx, jsonObject);
            });
        });
    }

    // Sends a message from one user to all users, along with a list of current usernames
    private static void searchResults(WsContext session, JSONObject queryTemplate) throws IOException,
            ParseException, JSQLParserException, SQLException {
        JSONArray params = queryTemplate.getJSONArray("params");
        List<String> listParams = new ArrayList<>();
        for (int paramCtr = 0; paramCtr < params.length(); paramCtr++){
            listParams.add(params.getString(paramCtr));
        }
        if (listParams.size() > 0) {
            // Parse query to template
            String sql = queryTemplate.getString("sql") + ";";
            Select sqlStatement = (Select) CCJSqlParserUtil.parse(sql);
            PlainSelect plainSelect = (PlainSelect) sqlStatement.getSelectBody();
            Expression whereExpression = plainSelect.getWhere();
            MatchingVisitor visitor = new MatchingVisitor();
            whereExpression.accept(visitor);

            Map<String, List<String>> resultMap = new HashMap<>();
            List<SelectItem> selectItems = plainSelect.getSelectItems();

            List<Column> columns = visitor.columns;

            JSONObject resultObjet = new JSONObject();
            // Matching all parameters in Lucene. TODO: support more parameters
            for (int paramCtr = 0; paramCtr < listParams.size(); paramCtr++) {
                String param = listParams.get(paramCtr);
                Column column = columns.get(paramCtr);

                ScoreDoc[] docs = FuzzySearch.search(param);

                Map<String, List<ScoreDoc>> planResults = SimpleVizPlanner.plan(docs, 2);

                for (String groupVal: planResults.keySet()) {
                    JSONArray resultArray = new JSONArray();
                    List<ScoreDoc> groupDocs = planResults.get(groupVal);


                    boolean isLiteral = groupVal.equals(searcher.doc(groupDocs.get(0).doc).get("content"));
                    for (ScoreDoc scoreDoc : groupDocs) {
                        Document hitDoc = searcher.doc(scoreDoc.doc);
                        String columnName = hitDoc.get("column");
                        String content = hitDoc.get("content");
                        double score = scoreDoc.score;
                        column.setColumnName(columnName);

                        // Build database query
                        String template = sqlStatement.toString() + ";";
                        PreparedStatement preparedStatement = connection.prepareStatement(template);
                        preparedStatement.setQueryTimeout(1);
                        String[] newParams = listParams.toArray(new String[0]);
                        newParams[paramCtr] = content;
                        for (int index = 0; index < newParams.length; index++) {
                            preparedStatement.setString(index + 1, newParams[index]);
                        }
                        // Result sets
                        ResultSet rs = preparedStatement.executeQuery();
                        for (SelectItem item: selectItems) {
                            resultMap.put(item.toString(), new ArrayList<>());
                        }

                        int nrRows = 0;
                        for (int rowCtr = 0; rs.next(); rowCtr++) {
                            for (int columnCtr = 1; columnCtr <= resultMap.size(); columnCtr++) {
                                resultMap.get(selectItems.get(columnCtr - 1).toString())
                                        .add(rs.getString(columnCtr));
                            }
                            nrRows++;
                        }
                        boolean isAgg = nrRows == 1 && selectItems.size() == 1;
                        JSONObject resultJson = new JSONObject(resultMap);

                        JSONObject result = new JSONObject();

                        String labelName = (isLiteral ? columnName : content).replace("_", " ");
                        result.put("template", template).put("params", Arrays.toString(newParams))
                                .put("score", score).put("results", resultJson).put("type", isAgg ? "agg" : "rows")
                                .put("label", labelName);
                        System.out.println(result.toString());
                        resultArray.put(result);
                        rs.close();
                        preparedStatement.close();
                    }
                    String titleName = isLiteral ? groupVal : "column " + groupVal;
                    resultObjet.put(titleName, resultArray);
                }
            }

            if (resultObjet.isEmpty()) {
                session.send("{}");
            }
            else {
                session.send(resultObjet.toString());
            }
        }
        else {
            session.send("{}");
        }
    }

}