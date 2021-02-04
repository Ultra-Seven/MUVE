package processor;

import config.HostConfig;
import config.PlanConfig;
import matching.FuzzySearch;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.json.JSONArray;
import org.json.JSONObject;
import planning.viz.SimpleVizPlanner;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Given number of queries, we find a execution plan to
 * generate intermediate results for multiple queries.
 * Different queries can save redundant operation by
 * sharing the intermediate results.
 *
 * @author Ziyun Wei
 */
public class MultiQueryProcessor {

    public static int[] initializePixels(Map<String, List<ScoreDoc>> resultPerRow) {
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
        return pixels;

    }

    public static JSONArray execute(List<Map<String, List<ScoreDoc>>> planResults,
                                    IndexSearcher searcher,
                                    PlainSelect plainSelect,
                                    Connection connection) throws IOException, SQLException {
        // Return an array of data points
        JSONArray resultRows = new JSONArray();
        // Create a rank map
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
        // SQL prefix
        List<SelectItem> selectItems = plainSelect.getSelectItems();
        List<String> selectItemsName = new ArrayList<>(selectItems.size());
        boolean isAgg = selectItems.size() == 1;
        for (SelectItem item: selectItems) {
            String selectStr = item.toString();
            isAgg = isAgg && selectStr.charAt(0) != '(';
            selectItemsName.add(item.toString());
        }
        FromItem fromItems = plainSelect.getFromItem();
        String selectBase = "SELECT " + String.join(", ", selectItemsName);
        String fromBase = "FROM " + fromItems.toString();
        Statement statement = connection.createStatement();
        // Iterate over all rows
        for (Map<String, List<ScoreDoc>> resultPerRow: planResults) {
            JSONObject plotObjet = new JSONObject();
            int[] pixels = initializePixels(resultPerRow);
            int groupCtr = 0;
            for (String groupVal: resultPerRow.keySet()) {
                JSONArray resultArray = new JSONArray();
                List<ScoreDoc> groupDocs = resultPerRow.get(groupVal);
                int nrDocs = groupDocs.size();

                Map<String, Float> scoreIndex = new HashMap<>(nrDocs);
                // First case
                ScoreDoc scoreDocFirst = groupDocs.get(0);
                Document hitDocFirst = searcher.doc(scoreDocFirst.doc);
                String contentFirst = hitDocFirst.get("text");
                String columnNameFirst = hitDocFirst.get("column");
                boolean isColumn = columnNameFirst.equals(groupVal);
                String labelFirst = isColumn ? contentFirst : columnNameFirst;
                float scoreFirst = scoreDocFirst.score;
                scoreIndex.put(labelFirst, scoreFirst);
                // Rest cases
                for (int docCtr = 1; docCtr < nrDocs; docCtr++) {
                    ScoreDoc scoreDoc = groupDocs.get(docCtr);
                    Document hitDoc = searcher.doc(scoreDoc.doc);
                    String columnName = hitDoc.get("column");
                    String content = hitDoc.get("text");
                    String label = isColumn ? content : columnName;
                    float score = scoreDoc.score;
                    scoreIndex.put(label, score);
                }

                // When the group value is a column?
                if (isColumn) {
                    String newSelect = selectBase + ", " + groupVal;
                    String predicate = groupVal + " in ('" + String.join("','", scoreIndex.keySet()) + "')";
                    String comprehensiveSQL = newSelect + " " + fromBase
                            + " WHERE " + predicate + " GROUP BY " + groupVal + ";";
                    ResultSet rs = statement.executeQuery(comprehensiveSQL);
                    while (rs.next()) {
                        // Query results
                        JSONObject resultObj = new JSONObject();
                        String result = rs.getString(1);
                        String literal = rs.getString(2);
                        String labelName = literal.replace("_", " ");
                        float score = scoreIndex.get(literal);
                        int rank = ranks.get(score);
                        resultObj.put("template", comprehensiveSQL)
                                .put("rank", rank).put("score", score)
                                .put("results", "[" + result + "]").put("type", isAgg ? "agg" : "rows")
                                .put("label", labelName).put("context", "column");
                        resultArray.put(resultObj);
                    }
                    rs.close();
                }
                // Execute in the normal way
                else {
                    for (Map.Entry<String, Float> scoreEntry: scoreIndex.entrySet()) {
                        String columnName = scoreEntry.getKey();
                        String predicate = columnName + "='" + groupVal + "'";
                        String singleSQL = selectBase + " " + fromBase
                                + " WHERE " + predicate +  ";";
                        ResultSet rs = statement.executeQuery(singleSQL);
                        JSONObject resultObj = new JSONObject();
                        String result = "";
                        String labelName = "";
                        while (rs.next()) {
                            // Query results
                            result = rs.getString(1);
                        }
                        labelName = columnName.replace("_", " ");
                        float score = scoreEntry.getValue();
                        int rank = ranks.get(score);
                        resultObj.put("template", singleSQL)
                                .put("rank", rank).put("score", score)
                                .put("results", "[" + result + "]").put("type", isAgg ? "agg" : "rows")
                                .put("label", labelName).put("context", "column");
                        resultArray.put(resultObj);
                        rs.close();
                    }

                }
                String titleName = groupVal.replace("_", " ");
                int nrPixels = pixels[groupCtr];
                plotObjet.put(titleName,
                        new JSONObject().put("data", resultArray).put("width", nrPixels));
                groupCtr++;
            }
            resultRows.put(plotObjet);
        }

        statement.close();
        return resultRows;
    }
    public static void main(String[] args) throws IOException, ParseException, JSQLParserException, SQLException {
        String url = HostConfig.DB_HOST;
        Properties props = new Properties();
        props.setProperty("user", "monetdb");
        props.setProperty("password", "monetdb");
        Connection connection = DriverManager.getConnection(url, props);
        String dataset = "dob_job";
        ScoreDoc[] docs = FuzzySearch.search("Bronx", dataset);
        IndexSearcher searcher = FuzzySearch.searchers.get(dataset);
        List<Map<String, List<ScoreDoc>>> results = SimpleVizPlanner.plan(docs, 2,
                PlanConfig.R, searcher);
        String template = "SELECT max(\"estimate_fee\") FROM dob_job WHERE \"city\" = ?;";
        Select sqlStatement = (Select) CCJSqlParserUtil.parse(template);
        PlainSelect plainSelect = (PlainSelect) sqlStatement.getSelectBody();
        long executionStart = System.currentTimeMillis();
        execute(results, searcher, plainSelect, connection);
        long executionEnd = System.currentTimeMillis();
        System.out.println(executionEnd - executionStart);
        connection.close();
    }
}
