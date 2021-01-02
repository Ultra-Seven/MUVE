package server;

import config.HostConfig;
import config.PlanConfig;
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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import planning.viz.GreedyPlanner;
import planning.viz.SimpleVizPlanner;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class LuceneServlet {
    private static Map<WsContext, String> userUsernameMap = new ConcurrentHashMap<>();
    private static int nextUserNumber = 1;
    private static Connection connection;

    private static Server createHttpsServer() {
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("0.0.0.0");
        connector.setPort(HostConfig.SERVER_PORT-1);
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.addCustomizer(new SecureRequestCustomizer());
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStorePath(LuceneServlet.class.getResource("/keystore.jks").toExternalForm());
        sslContextFactory.setKeyStorePassword("password");
        sslContextFactory.setKeyManagerPassword("password");

        ServerConnector sslConnector = new ServerConnector(server,
                new SslConnectionFactory(sslContextFactory, "http/1.1"),
                new HttpConnectionFactory(httpConfig));
        sslConnector.setPort(HostConfig.SERVER_PORT);
        sslConnector.setHost("0.0.0.0");
        server.setConnectors(new ServerConnector[] { connector, sslConnector });
        return server;
    }

    public static void main(String[] args) throws SQLException {
        Server server = createHttpsServer();
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

        app.post("/", ctx -> {
            // some code
            String message = ctx.body();
            System.out.println(message);
            ctx.result("[]");
        });

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

                int width = (int) Math.floor(Double.parseDouble(query_list[2]));

                ProcessBuilder processBuilder = new ProcessBuilder(commands);
                Process process = processBuilder.start();

                String result = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))
                        .lines()
                        .collect(Collectors.joining("\n"));
                System.out.println(result);
                JSONObject jsonObject = new JSONObject(result);
                try {
                    searchResults(ctx, jsonObject, query_list[0], width, query_list[3]);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Error");
                    ctx.send("{\"data\": [], debug: {}}");
                }
            });
        });
    }

    // Sends a message from one user to all users, along with a list of current usernames
    private static void searchResults(WsContext session,
                                      JSONObject queryTemplate,
                                      String dataset,
                                      int width,
                                      String planner) throws IOException,
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
            JSONObject resultObj = new JSONObject();
            JSONArray resultRows = new JSONArray();
            // Matching all parameters in Lucene. TODO: support more parameters
            int R = Math.min(PlanConfig.R, width);
            for (int paramCtr = 0; paramCtr < listParams.size(); paramCtr++) {
                String param = listParams.get(paramCtr);
                Column column = columns.get(paramCtr);

                ScoreDoc[] docs = FuzzySearch.search(param, dataset);
                IndexSearcher searcher = FuzzySearch.searchers.get(dataset);
                if (docs.length == 0) {
                    continue;
                }
                List<Map<String, List<ScoreDoc>>> planResults;
                if (planner.equals("ilp")) {
                    planResults = SimpleVizPlanner.plan(docs, PlanConfig.NR_ROWS, R, searcher);
                }
                else {
                    planResults = GreedyPlanner.plan(docs, PlanConfig.NR_ROWS, R, searcher);
                }
                Set<Float> scoreSet = new LinkedHashSet<>();
                planResults.forEach(row ->
                        row.values().forEach(
                                scoreDocs -> scoreDocs.forEach(
                                        doc -> scoreSet.add(doc.score)
                                )
                        )
                );
                List<Float> scoreList = scoreSet.stream().sorted().collect(Collectors.toList());
                int nrScores = scoreList.size();
                Map<Float, Integer> ranks = new HashMap<>(nrScores);
                for (int scoreCtr = 0; scoreCtr < nrScores; scoreCtr++) {
                    float scoreElement = scoreList.get(nrScores - scoreCtr - 1);
                    ranks.put(scoreElement, scoreCtr);
                }
                for (Map<String, List<ScoreDoc>> resultPerRow: planResults) {
                    JSONObject resultObjet = new JSONObject();
                    // Counting widths
                    int size = resultPerRow.size();
                    int[] pixels = new int[size];
                    int groupCtr = 0;
                    for (String groupVal: resultPerRow.keySet()) {
                        pixels[groupCtr] = resultPerRow.get(groupVal).size() * PlanConfig.B + PlanConfig.C;
                        groupCtr++;
                    }
                    int sumPixels = Arrays.stream(pixels).sum();
                    for (int group = 0; group < size - 1; group++) {
                        pixels[group] = (int) Math.round((pixels[group] + 0.0) / sumPixels * 100);
                    }
                    pixels[size - 1] = 0;
                    pixels[size - 1] = 100 - Arrays.stream(pixels).sum();
                    groupCtr = 0;
                    for (String groupVal: resultPerRow.keySet()) {
                        JSONArray resultArray = new JSONArray();
                        List<ScoreDoc> groupDocs = resultPerRow.get(groupVal);

                        boolean isLiteral = groupVal.equals(searcher.doc(groupDocs.get(0).doc).get("text"));
                        for (ScoreDoc scoreDoc : groupDocs) {
                            Document hitDoc = searcher.doc(scoreDoc.doc);
                            String columnName = hitDoc.get("column");
                            String content = hitDoc.get("text");
                            float score = scoreDoc.score;
                            column.setColumnName("\"" + columnName + "\"");

                            boolean selectAgg = true;
                            for (SelectItem item: selectItems) {
                                resultMap.put(item.toString(), new ArrayList<>());
                                if (selectAgg) {
                                    selectAgg = item.toString().indexOf("(") > 0;
                                }
                            }

                            // Build database query
                            String template = sqlStatement.toString() + ";";
                            System.out.println(template);
                            PreparedStatement preparedStatement = connection.prepareStatement(template);
                            preparedStatement.setQueryTimeout(1);
                            String[] newParams = listParams.toArray(new String[0]);
                            newParams[paramCtr] = content;
                            for (int index = 0; index < newParams.length; index++) {
                                preparedStatement.setString(index + 1, newParams[index]);
                            }
                            // Result sets
                            ResultSet rs = preparedStatement.executeQuery();

                            int nrRows = 0;
                            for (int rowCtr = 0; rs.next(); rowCtr++) {
                                for (int columnCtr = 1; columnCtr <= resultMap.size(); columnCtr++) {
                                    resultMap.get(selectItems.get(columnCtr - 1).toString())
                                            .add(rs.getString(columnCtr));
                                }
                                nrRows++;
                            }

                            boolean isAgg = nrRows == 1 && selectItems.size() == 1 && selectAgg;
                            JSONObject resultJson = new JSONObject(resultMap);

                            JSONObject result = new JSONObject();

                            String labelName = (isLiteral ? columnName : content).replace("_", " ");
                            int rank = ranks.get(score);
                            result.put("template", template).put("params", Arrays.toString(newParams))
                                    .put("rank", rank).put("score", score)
                                    .put("results", resultJson).put("type", isAgg ? "agg" : "rows")
                                    .put("label", labelName).put("context", isLiteral ? "value" : "column");
                            System.out.println(result.toString());
                            resultArray.put(result);
                            rs.close();
                            preparedStatement.close();
                        }
                        String titleName = (isLiteral ? groupVal : groupVal)
                                .replace("_", " ");
                        int nrPixels = pixels[groupCtr];
                        resultObjet.put(titleName,
                                new JSONObject().put("data", resultArray).put("width", nrPixels));
                        groupCtr++;
                    }
                    resultRows.put(resultObjet);
                }
            }
            resultObj.put("data", resultRows);
            resultObj.put("debug", new JSONArray().put(queryTemplate));
            session.send(resultObj.toString());
        }
        else {
            session.send(
                    new JSONObject().put("data", new JSONArray())
                    .put("debug", new JSONArray().put(queryTemplate))
                    .toString()
            );
        }
    }

}