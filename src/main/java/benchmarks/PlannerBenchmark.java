package benchmarks;

import config.PlanConfig;
import matching.FuzzySearch;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import planning.viz.GreedyPlanner;
import planning.viz.SimpleVizPlanner;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class PlannerBenchmark {
    public static String[] testQueries = new String[]{
            "Bronx"
    };
    public static String dataset = "dob_job";
    public final static int NR_CASES = 1;
    public static Pair<Double, Double> runCase(int topK, int nrRows, boolean isGreedy) throws IOException, ParseException {
        List<Long> timerGreedy = new ArrayList<>();
        List<Double> utilityGreedy = new ArrayList<>();
        for (int testCtr = 0; testCtr < NR_CASES; testCtr++) {
            for (String query: testQueries) {
                ScoreDoc[] docs = FuzzySearch.search(query, dataset, topK);
                if (topK != docs.length) {
                    System.out.println("Inconsistent rows for " + query);
                    System.exit(0);
                }
                IndexSearcher searcher = FuzzySearch.searchers.get(dataset);
                long timer1 = System.currentTimeMillis();
                List<Map<String, List<ScoreDoc>>> results =
                        isGreedy ? GreedyPlanner.plan(docs, nrRows, PlanConfig.R, searcher)
                        : SimpleVizPlanner.plan(docs, nrRows, PlanConfig.R, searcher);
                long timer2 = System.currentTimeMillis();
                timerGreedy.add(timer2 - timer1);
                double utility = 0;
                int rowCtr = 1;
                for (Map<String, List<ScoreDoc>> rowResults : results) {
                    System.out.println("Row: " + rowCtr);
                    for (String plotName : rowResults.keySet()) {
                        List<String> queries = new ArrayList<>();
                        for (ScoreDoc scoreDoc : rowResults.get(plotName)) {
                            double score = scoreDoc.score;
                            Document document = searcher.doc(scoreDoc.doc);
                            utility += score;
                            queries.add(document.get("column") + "=" + document.get("text") + ":" + score);
                        }
                        System.out.println("Plot: " + plotName + "\t" + "Queries: " + String.join(",", queries));
                    }
                    rowCtr++;
                }
                System.out.println("Utility: " + utility);
                utilityGreedy.add(utility);
            }
        }

        long totalMillis = timerGreedy.stream()
                .reduce(0L, Long::sum);
        long number = timerGreedy.size();
        double totalUtility = utilityGreedy.stream().reduce(0.0, Double::sum);
        double averageTime = (totalMillis + 0.0) / number;
        double averageUtility = (totalUtility + 0.0) / number;
        return new ImmutablePair<>(averageTime, averageUtility);
    }


    public static void varyTopK() throws IOException, ParseException {
        int[] topKs = new int[]{50};
        FileWriter fw = new FileWriter("planner_varyK.csv");
        fw.write("TopK,Rows,Width,Time,Utility,Planner\n");
        // Greedy
        for (Integer topK: topKs) {
            System.out.println("TopK: " + topK);
            Pair<Double, Double> result = runCase(topK, 2, true);
            fw.write(topK + ",2,900," + result.getLeft() + "," + result.getRight() + ",Greedy\n");

        }

        // ILP
        for (Integer topK: topKs) {
            System.out.println("TopK: " + topK);
            Pair<Double, Double> result = runCase(topK, 2, false);
            fw.write(topK + ",2,900," + result.getLeft() + "," + result.getRight() + ",ILP\n");
        }

        fw.close();

    }

    public static void varyRows() throws IOException, ParseException {
        int[] rows = new int[]{1, 2, 3, 4, 5};
        FileWriter fw = new FileWriter("planner_varyRows.csv");
        fw.write("TopK,Rows,Width,Time,Utility,Planner\n");
        // Greedy
        for (Integer row: rows) {
            System.out.println("Row: " + row);
            Pair<Double, Double> result = runCase(100, row, true);
            fw.write("100," + row + ",900," + result.getLeft() + "," + result.getRight() + ",Greedy\n");

        }

        // ILP
        for (Integer row: rows) {
            System.out.println("Row: " + row);
            Pair<Double, Double> result = runCase(100, row, false);
            fw.write("100," + row + ",900," + result.getLeft() + "," + result.getRight() + ",ILP\n");
        }

        fw.close();

    }

    public static void main(String[] args) throws IOException, ParseException {
        varyTopK();
//        varyRows();
    }
}
