package benchmarks;

import config.PlanConfig;
import planning.query.QueryFactory;
import planning.viz.PlotGreedyPlanner;
import planning.viz.WaitTimePlanner;
import stats.PlanStats;

import java.io.*;
import java.util.*;

public class PlannerBenchmark {
    public static String dataset = "sample_311";
    public final static int NR_CASES = 1;


    public static boolean runMUVEQuery(String query, boolean isStatic) {
        try {
            QueryFactory queryFactory = new QueryFactory(query);
            PlotGreedyPlanner.plan(queryFactory.queries, queryFactory.nrDistinctValues,
                    2, PlanConfig.R, queryFactory, isStatic);
            return true;

        } catch (Exception exception) {
            System.out.println("No similar queries!");
            return false;
        }
    }

    public static boolean runILPQuery(String query) {
        try {
            QueryFactory queryFactory = new QueryFactory(query);
            WaitTimePlanner.plan(queryFactory.queries, queryFactory.nrDistinctValues,
                    PlanConfig.NR_ROWS, PlanConfig.R, queryFactory);
            return true;
        } catch (Exception exception) {
            exception.printStackTrace();
            System.out.println("Error when running queries!");
            return false;
        }
    }

    public static void benchmarkResolution(int sys) throws IOException {
        PrintWriter printWriter;
        if (sys == 0) {
            printWriter = new PrintWriter("varyResolutions_ilp.csv");
        }
        else if (sys == 1) {
            printWriter = new PrintWriter("varyResolutions_greedy.csv");
        }
        else {
            printWriter = new PrintWriter("varyResolutions_opt.csv");
        }
        // Iphone X, surface duo, ipad, ipad pro, laptop
        int[] resolutions = new int[]{375, 540, 768, 1024, 1680};
        printWriter.println("Query\tResolutions\tNrPlots\tWaitTime\tInitMillis" +
                "\tBuildMillis\tOptimizeMillis\tIsTimeout");
        for (int resolutionCtr = 0; resolutionCtr < resolutions.length; resolutionCtr++) {
            PlanConfig.R = resolutions[resolutionCtr];
            int queryCtr = 0;
            for(String query: readQueries()) {
                System.out.println(query);
                boolean success = sys == 0 ? runILPQuery(query) : runMUVEQuery(query, sys == 1);
                queryCtr++;
                if (success) {
                    printWriter.print(queryCtr + "\t");
                    printWriter.print(PlanConfig.R + "\t");
                    printWriter.print(PlanStats.nrPlots + "\t");
                    printWriter.print(PlanStats.waitTime + "\t");
                    printWriter.print(PlanStats.initMillis + "\t");
                    printWriter.print(PlanStats.buildMillis + "\t");
                    printWriter.print(PlanStats.optimizeMillis + "\t");
                    printWriter.println(PlanStats.isTimeout);
                }
            }
        }
        printWriter.close();
    }

    public static void benchmarkRows(int sys) throws IOException {
        PrintWriter printWriter;
        if (sys == 0) {
            printWriter = new PrintWriter("varyRows_ilp.csv");
        }
        else if (sys == 1) {
            printWriter = new PrintWriter("varyRows_greedy.csv");
        }
        else {
            printWriter = new PrintWriter("varyRows_opt.csv");
        }
        // Iphone X, surface duo, ipad, ipad pro, laptop
        int[] rows = new int[]{1, 2, 3, 4, 5};
        printWriter.println("Query\tRows\tNrPlots\tWaitTime\tInitMillis" +
                "\tBuildMillis\tOptimizeMillis\tIsTimeout");
        for (int rowCtr = 0; rowCtr < rows.length; rowCtr++) {
            PlanConfig.NR_ROWS = rows[rowCtr];
            int queryCtr = 0;
            for(String query: readQueries()) {
                System.out.println(query);
                boolean success = sys == 0 ? runILPQuery(query) : runMUVEQuery(query, sys == 1);
                queryCtr++;
                if (success) {
                    printWriter.print(queryCtr + "\t");
                    printWriter.print(PlanConfig.NR_ROWS + "\t");
                    printWriter.print(PlanStats.nrPlots + "\t");
                    printWriter.print(PlanStats.waitTime + "\t");
                    printWriter.print(PlanStats.initMillis + "\t");
                    printWriter.print(PlanStats.buildMillis + "\t");
                    printWriter.print(PlanStats.optimizeMillis + "\t");
                    printWriter.println(PlanStats.isTimeout);
                }
            }
        }
        printWriter.close();
    }

    public static void benchmarkTopK(int sys) throws IOException {
        PrintWriter printWriter;
        if (sys == 0) {
            printWriter = new PrintWriter("varyTopK_ilp.csv");
        }
        else if (sys == 1) {
            printWriter = new PrintWriter("varyTopK_greedy.csv");
        }
        else {
            printWriter = new PrintWriter("varyTopK_opt.csv");
        }
        int[] nrQueriesArray = new int[]{5, 10, 20};
        printWriter.println("Query\tTopK\tNrPlots\tWaitTime\tInitMillis" +
                "\tBuildMillis\tOptimizeMillis\tIsTimeout");
        for (int nrQueryCtr = 0; nrQueryCtr < nrQueriesArray.length; nrQueryCtr++) {
            PlanConfig.TOPK = nrQueriesArray[nrQueryCtr];
            int queryCtr = 0;
            for(String query: readQueries()) {
                System.out.println(query);
                boolean success = sys == 0 ? runILPQuery(query) : runMUVEQuery(query, sys == 1);
                queryCtr++;
                if (success) {
                    printWriter.print(queryCtr + "\t");
                    printWriter.print(PlanConfig.TOPK + "\t");
                    printWriter.print(PlanStats.nrPlots + "\t");
                    printWriter.print(PlanStats.waitTime + "\t");
                    printWriter.print(PlanStats.initMillis + "\t");
                    printWriter.print(PlanStats.buildMillis + "\t");
                    printWriter.print(PlanStats.optimizeMillis + "\t");
                    printWriter.println(PlanStats.isTimeout);
                }
            }
        }
        printWriter.close();
    }

    public static List<String> readQueries() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader("./queries/" + dataset + "/queries.sql"));
        String sql = reader.readLine();
        List<String> queries = new ArrayList<>();
        while (sql != null) {
            queries.add(sql);
            sql = reader.readLine();
        }
        return queries;
    }

    public static void main(String[] args) throws IOException {
//        varyTopK();
//        varyRows();
//        benchmarkTopK(1);
//        benchmarkResolution(1);
//        benchmarkRows(1);
        benchmarkTopK(2);
        benchmarkResolution(2);
        benchmarkRows(2);
    }
}
