package planning.viz;

import config.PlanConfig;
import matching.FuzzySearch;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class PlannerTest {
    public static void main(String[] args) throws IOException, ParseException {
        String dataset = "sample_au";
        ScoreDoc[] docs = FuzzySearch.search("marketing", dataset);
        IndexSearcher searcher = FuzzySearch.searchers.get(dataset);
        long timer1 = System.currentTimeMillis();
        Arrays.sort(docs, (doc1, doc2) -> Double.compare(doc2.score, doc1.score));
        int nrRows = 2;
        List<Map<String, List<ScoreDoc>>> results =
                GreedyPlanner.plan(docs, nrRows, PlanConfig.R, searcher);
//        List<Map<String, List<ScoreDoc>>> results =
//                SimpleVizPlanner.plan(docs, nrRows, PlanConfig.R, searcher);
        long timer2 = System.currentTimeMillis();
        System.out.println(timer2 - timer1);
        double utility = 0;
        for (int rowCtr = 0; rowCtr < results.size(); rowCtr++) {
            System.out.println("Row: " + rowCtr);
            System.out.println("=================");
            Map<String, List<ScoreDoc>> rowResults = results.get(rowCtr);
            for (String plotName: rowResults.keySet()) {
                System.out.println("[Plot]: " + plotName);
                for (ScoreDoc scoreDoc: rowResults.get(plotName)) {
                    double score = scoreDoc.score;
                    int docCtr = scoreDoc.doc;
                    Document hitDoc = searcher.doc(docCtr);
                    String column = hitDoc.get("column");
                    String content = hitDoc.get("text");
                    System.out.println(column + "=" + content + ": " + score);
                    utility += score;
                }
            }
        }
        System.out.println("Score: " + utility);
    }
}
