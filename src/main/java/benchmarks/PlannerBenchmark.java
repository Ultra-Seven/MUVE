package benchmarks;

import config.PlanConfig;
import connector.DBConnector;
import connector.PSQLConnector;
import planning.query.QueryFactory;
import planning.viz.DataPoint;
import planning.viz.PlotGreedyPlanner;
import planning.viz.WaitTimeGurobiPlanner;
import planning.viz.WaitTimePlanner;
import stats.PlanStats;
import stats.ProcessingStats;

import java.io.*;
import java.util.*;

public class PlannerBenchmark {
    public static String dataset = "sample_311";
    public static Map<String, String> columnsMap = new HashMap<>();
    public final static int NR_CASES = 1;


    public static boolean runMUVEQuery(String query, boolean isStatic) {
        try {
            QueryFactory queryFactory = new QueryFactory(query);
            PlotGreedyPlanner.plan(queryFactory.queries, queryFactory.nrDistinctValues,
                    PlanConfig.NR_ROWS, PlanConfig.R, queryFactory, isStatic);
            return true;

        } catch (Exception exception) {
            System.out.println("No similar queries!");
            return false;
        }
    }

    public static boolean runMUVEExecuteQuery(String query, boolean isStatic) {
        try {
            QueryFactory queryFactory = new QueryFactory(query);
            List<Map<String, List<DataPoint>>> optimalPlan =
                    PlotGreedyPlanner.plan(queryFactory.queries, queryFactory.nrDistinctValues,
                    PlanConfig.NR_ROWS, PlanConfig.R, queryFactory, isStatic);
            List<DataPoint> bestDataPoints = new ArrayList<>();
            optimalPlan.forEach(map -> map.values().forEach(bestDataPoints::addAll));
            String mergedQuery = queryFactory.mergeQueries(bestDataPoints, query);
            long timer1 = System.currentTimeMillis();
            DBConnector connection = PSQLConnector.getConnector();
            connection.execute(mergedQuery);
            long timer2 = System.currentTimeMillis();
            ProcessingStats.executeMillis = timer2 - timer1;
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

    public static boolean runGUROBIQuery(String query) {
        try {
            QueryFactory queryFactory = new QueryFactory(query);
            WaitTimeGurobiPlanner.plan(queryFactory.queries, queryFactory.nrDistinctValues,
                    PlanConfig.NR_ROWS, PlanConfig.R, queryFactory);
            return true;
        } catch (Exception exception) {
            exception.printStackTrace();
            System.out.println("Error when running queries!");
            return false;
        }
    }

    public static boolean runGUROBIExecuteQuery(String query) {
        try {
            QueryFactory queryFactory = new QueryFactory(query);
            List<Map<String, List<DataPoint>>> optimalPlan =
                    WaitTimeGurobiPlanner.plan(queryFactory.queries, queryFactory.nrDistinctValues,
                    PlanConfig.NR_ROWS, PlanConfig.R, queryFactory);
            List<DataPoint> bestDataPoints = new ArrayList<>();
            optimalPlan.forEach(map -> map.values().forEach(bestDataPoints::addAll));
            String mergedQuery = queryFactory.mergeQueries(bestDataPoints, query);
            long timer1 = System.currentTimeMillis();
            DBConnector connection = PSQLConnector.getConnector();
            connection.execute(mergedQuery);
            long timer2 = System.currentTimeMillis();
            ProcessingStats.executeMillis = timer2 - timer1;
            return true;
        } catch (Exception exception) {
            exception.printStackTrace();
            System.out.println("Error when running queries!");
            return false;
        }
    }

    public static boolean runMergeQuery(String query) {
        try {
            QueryFactory queryFactory = new QueryFactory(query);
            String mergedQuery = queryFactory.mergeQueries(Arrays.asList(queryFactory.queries), query);
            long timer1 = System.currentTimeMillis();
            DBConnector connection = PSQLConnector.getConnector();
            connection.execute(mergedQuery);
            long timer2 = System.currentTimeMillis();
            ProcessingStats.executeMillis = timer2 - timer1;
            return true;
        } catch (Exception exception) {
            exception.printStackTrace();
            System.out.println("Error when running queries!");
            return false;
        }
    }

    public static boolean runSplitQuery(String query) {
        String sql = null;
        try {
            QueryFactory queryFactory = new QueryFactory(query);

            long timer1 = System.currentTimeMillis();
            for (String newSQL: queryFactory.splitQueries(query)) {
                sql = newSQL;
                DBConnector connection = PSQLConnector.getConnector();
                connection.execute(newSQL);
            }
            long timer2 = System.currentTimeMillis();
            ProcessingStats.executeMillis = timer2 - timer1;
            return true;
        } catch (Exception exception) {
            exception.printStackTrace();
            System.out.println("Error when running queries!" + sql);
            return false;
        }
    }

    public static void benchmarkResolution(int sys) throws IOException {
        PlanConfig.PROCESSING_WEIGHT = 0;
        PlanConfig.NR_ROWS = 1;
        PlanConfig.TOPK = 20;
        PrintWriter printWriter;
        if (sys == 0) {
            printWriter = new PrintWriter(dataset + "_varyResolutions_ilp.csv");
        }
        else if (sys == 1) {
            printWriter = new PrintWriter(dataset + "_varyResolutions_greedy.csv");
        }
        else {
            printWriter = new PrintWriter(dataset + "_varyResolutions_opt.csv");
        }
        // Iphone X, surface duo, ipad, ipad pro, laptop
        int[] resolutions = new int[]{375, 540, 768, 1024, 1680};
        printWriter.println("Query\tResolutions\tNrPlots\tNrPredicates\tWaitTime\tInitMillis" +
                "\tBuildMillis\tOptimizeMillis\tIsTimeout");
        for (int resolutionCtr = 0; resolutionCtr < resolutions.length; resolutionCtr++) {
            PlanConfig.R = resolutions[resolutionCtr];
            int queryCtr = 0;
            for(String query: readQueries()) {
                System.out.println(query);
                boolean success = sys == 0 ? runGUROBIQuery(query) : runMUVEQuery(query, sys == 1);
                queryCtr++;
                if (success) {
                    printWriter.print(queryCtr + "\t");
                    printWriter.print(PlanConfig.R + "\t");
                    printWriter.print(PlanStats.nrPlots + "\t");
                    printWriter.print(PlanStats.nrPredicates + "\t");
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
        PlanConfig.PROCESSING_WEIGHT = 0;
        PlanConfig.TOPK = 20;
        PlanConfig.R = 300;
        PrintWriter printWriter;
        if (sys == 0) {
            printWriter = new PrintWriter(dataset + "_varyRows_ilp.csv");
        }
        else if (sys == 1) {
            printWriter = new PrintWriter(dataset + "_varyRows_greedy.csv");
        }
        else {
            printWriter = new PrintWriter(dataset + "_varyRows_opt.csv");
        }
        // Iphone X, surface duo, ipad, ipad pro, laptop
        int[] rows = new int[]{1, 2, 3, 4, 5};
        printWriter.println("Query\tRows\tNrPlots\tNrPredicates\tWaitTime\tInitMillis" +
                "\tBuildMillis\tOptimizeMillis\tIsTimeout");
        for (int rowCtr = 0; rowCtr < rows.length; rowCtr++) {
            PlanConfig.NR_ROWS = rows[rowCtr];
            int queryCtr = 0;
            for(String query: readQueries()) {
                System.out.println(query);
                boolean success = sys == 0 ? runGUROBIQuery(query) : runMUVEQuery(query, sys == 1);
                queryCtr++;
                if (success) {
                    printWriter.print(queryCtr + "\t");
                    printWriter.print(PlanConfig.NR_ROWS + "\t");
                    printWriter.print(PlanStats.nrPlots + "\t");
                    printWriter.print(PlanStats.nrPredicates + "\t");
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

    public static void benchmarkWeights(int sys) throws IOException {
        PlanConfig.TOPK = 20;
        PlanConfig.R = 300;
        PlanConfig.NR_ROWS = 1;
        PrintWriter printWriter;
        if (sys == 0) {
            printWriter = new PrintWriter(dataset + "_varyWeights_ilp.csv");
        }
        else if (sys == 1) {
            printWriter = new PrintWriter(dataset + "_varyWeights_ilp.csv");
        }
        else {
            printWriter = new PrintWriter(dataset + "_varyRows_opt.csv");
        }
        // Iphone X, surface duo, ipad, ipad pro, laptop
        double[] weights = new double[]{0, 1, 2, 3, 4, 1000};
        printWriter.println("Query\tRows\tNrPlots\tNrPredicates\tWaitTime\tInitMillis" +
                "\tBuildMillis\tOptimizeMillis\tIsTimeout");
        for (int weightCtr = 0; weightCtr < weights.length; weightCtr++) {
            PlanConfig.PROCESSING_WEIGHT = weights[weightCtr];
            int queryCtr = 0;
            for(String query: readQueries()) {
                System.out.println(query);
                boolean success = sys == 0 ? runGUROBIQuery(query) : runMUVEQuery(query, sys == 1);
                queryCtr++;
                if (success) {
                    printWriter.print(queryCtr + "\t");
                    printWriter.print(PlanConfig.NR_ROWS + "\t");
                    printWriter.print(PlanStats.nrPlots + "\t");
                    printWriter.print(PlanStats.nrPredicates + "\t");
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

    public static void benchmarkMerge(int sys) throws IOException {
        String[] datasets = new String[]{"dob_job"};
        columnsMap.put("applicant_first_name", "Applicant's First Name");
        columnsMap.put("applicant_last_name", "Applicant's Last Name");
        columnsMap.put("street", "Street Name");
        columnsMap.put("owner_last_name", "Owner's Last Name");
        columnsMap.put("job_description", "Job Description");
        columnsMap.put("owner_first_name", "Owner's First Name");
        columnsMap.put("city", "City ");
        columnsMap.put("business_name", "Owner's Business Name");
        columnsMap.put("owner_house_street", "Owner'sHouse Street Name");
        columnsMap.put("owner_type", "Owner Type");
        columnsMap.put("fee_status", "Fee Status");
        columnsMap.put("state", "State");
        columnsMap.put("applicant_title", "Applicant Professional Title");
        columnsMap.put("job_status_descrption", "Job Status Descrp");
        PrintWriter printWriter;
        if (sys == 0) {
            printWriter = new PrintWriter("splitQueries.csv");
        }
        else {
            printWriter = new PrintWriter("mergeQueries.csv");
        }
        printWriter.println("Dataset\tQuery\tNrPredicates\tProcessingMillis");
        for (String dataset: datasets) {
            PlannerBenchmark.dataset = dataset;
            String[] onePredicate = new String[] {
                    "SELECT count(*) FROM dob_job WHERE \"Applicant's First Name\"='ANATOLE';",
                    "SELECT count(*) FROM dob_job WHERE \"Owner's First Name\"='ALDEN';",
                    "SELECT count(*) FROM dob_job WHERE \"Owner's Last Name\"='FRIEDBERG';",
                    "SELECT count(*) FROM dob_job WHERE \"City \"='LONG ISL. CITY';",
                    "SELECT count(*) FROM dob_job WHERE \"Owner Type\"='NYCHA';",
                    "SELECT count(*) FROM dob_job WHERE \"Owner's Last Name\"='WU';",
                    "SELECT count(*) FROM dob_job WHERE \"Fee Status\"='EXEMPT';",
                    "SELECT count(*) FROM dob_job WHERE \"City \"='GREENLAWN';",
                    "SELECT count(*) FROM dob_job WHERE \"Applicant's First Name\"='MUSHARRAF'",
                    "SELECT count(*) FROM dob_job WHERE \"Owner's First Name\"='LEONARDO';"
            };
            int queryCtr = 0;
            for (String query: onePredicate) {
                System.out.println(query);
                boolean success = sys == 0 ? runSplitQuery(query) : runMergeQuery(query);
                queryCtr++;
                if (success) {
                    printWriter.print(dataset + "\t");
                    printWriter.print(queryCtr + "\t");
                    printWriter.print("1\t");
                    printWriter.println(ProcessingStats.executeMillis);
                }
            }
        }
        printWriter.close();
    }

    public static void benchMarkProcessing(int sys) throws IOException {
        PlanConfig.PROCESSING_WEIGHT = 0;
        PlanConfig.TOPK = 20;
        PlanConfig.R = 900;
        PlanConfig.NR_ROWS = 1;
        columnsMap.put("applicant_first_name", "Applicant's First Name");
        columnsMap.put("applicant_last_name", "Applicant's Last Name");
        columnsMap.put("street", "Street Name");
        columnsMap.put("owner_last_name", "Owner's Last Name");
        columnsMap.put("job_description", "Job Description");
        columnsMap.put("owner_first_name", "Owner's First Name");
        columnsMap.put("city", "City ");
        columnsMap.put("business_name", "Owner's Business Name");
        columnsMap.put("owner_house_street", "Owner'sHouse Street Name");
        columnsMap.put("owner_type", "Owner Type");
        columnsMap.put("fee_status", "Fee Status");
        columnsMap.put("state", "State");
        columnsMap.put("applicant_title", "Applicant Professional Title");
        columnsMap.put("job_status_descrption", "Job Status Descrp");

        String[] onePredicate = new String[] {
                "SELECT count(*) FROM dob_job WHERE \"Applicant's First Name\"='ANATOLE';",
                "SELECT count(*) FROM dob_job WHERE \"Owner's First Name\"='ALDEN';",
                "SELECT count(*) FROM dob_job WHERE \"Owner's Last Name\"='FRIEDBERG';",
                "SELECT count(*) FROM dob_job WHERE \"City \"='LONG ISL. CITY';",
                "SELECT count(*) FROM dob_job WHERE \"Owner Type\"='NYCHA';",
                "SELECT count(*) FROM dob_job WHERE \"Owner's Last Name\"='WU';",
                "SELECT count(*) FROM dob_job WHERE \"Fee Status\"='EXEMPT';",
                "SELECT count(*) FROM dob_job WHERE \"City \"='GREENLAWN';",
                "SELECT count(*) FROM dob_job WHERE \"Applicant's First Name\"='MUSHARRAF'",
                "SELECT count(*) FROM dob_job WHERE \"Owner's First Name\"='LEONARDO';"
        };
        PrintWriter printWriter;
        if (sys == 0) {
            WaitTimeGurobiPlanner.Processing_Constraint = true;
            printWriter = new PrintWriter("large_dob_varyCost_ilpC.csv");
        }
        else if (sys == 1) {
            WaitTimeGurobiPlanner.Processing_Constraint = false;
            printWriter = new PrintWriter("large_dob_varyCost_ilp.csv");
        }
        else {
            printWriter = new PrintWriter("large_dob_varyCost_greedy.csv");
        }
        int queryCtr = 0;
        int[] processing = new int[]{100, 200, 300, 400, 500, 600};
        printWriter.println("Query\tCost\tNrPlots\tNrPredicates\tWaitTime\tInitMillis" +
                "\tBuildMillis\tOptimizeMillis\tExecutionMillis\tIsTimeout");
        for (int processingCtr = 0; processingCtr < processing.length; processingCtr++) {
            PlanConfig.MAX_PROCESSING_COST = processing[processingCtr];
            for (String query: onePredicate) {
                System.out.println(query);
                boolean success;
                if (sys == 0) {
                    success = runGUROBIExecuteQuery(query);
                }
                else if (sys == 1) {
                    success = runGUROBIExecuteQuery(query);
                }
                else {
                    success = runMUVEExecuteQuery(query, true);
                }
                queryCtr++;
                if (success) {
                    printWriter.print(queryCtr + "\t");
                    printWriter.print(PlanConfig.MAX_PROCESSING_COST + "\t");
                    printWriter.print(PlanStats.nrPlots + "\t");
                    printWriter.print(PlanStats.nrPredicates + "\t");
                    printWriter.print(PlanStats.waitTime + "\t");
                    printWriter.print(PlanStats.initMillis + "\t");
                    printWriter.print(PlanStats.buildMillis + "\t");
                    printWriter.print(PlanStats.optimizeMillis + "\t");
                    printWriter.print(ProcessingStats.executeMillis + "\t");
                    printWriter.println(PlanStats.isTimeout);
                }
            }
        }
        printWriter.close();
    }

    public static void benchmarkTopK(int sys) throws IOException {
        PrintWriter printWriter;
        PlanConfig.R = 300;
        PlanConfig.PROCESSING_WEIGHT = 0;
        PlanConfig.NR_ROWS = 1;
        if (sys == 0) {
            printWriter = new PrintWriter(dataset + "_varyTopK_ilp.csv");
        }
        else if (sys == 1) {
            printWriter = new PrintWriter(dataset + "_varyTopK_greedy.csv");
        }
        else {
            printWriter = new PrintWriter(dataset + "_varyTopK_opt.csv");
        }
        int[] nrQueriesArray = new int[]{5, 10, 20, 30, 40, 50};
        printWriter.println("Query\tTopK\tNrPlots\tNrPredicates\tWaitTime\tInitMillis" +
                "\tBuildMillis\tOptimizeMillis\tIsTimeout");
        for (int nrQueryCtr = 0; nrQueryCtr < nrQueriesArray.length; nrQueryCtr++) {
            PlanConfig.TOPK = nrQueriesArray[nrQueryCtr];
            System.out.println("Evaluating TopK: " + PlanConfig.TOPK);
            int queryCtr = 0;
            for(String query: readQueries()) {
                System.out.println(query);
                boolean success = sys == 0 ?
                        runGUROBIQuery(query) : runMUVEQuery(query, sys == 1);
                queryCtr++;
                if (success) {
                    printWriter.print(queryCtr + "\t");
                    printWriter.print(PlanConfig.TOPK + "\t");
                    printWriter.print(PlanStats.nrPlots + "\t");
                    printWriter.print(PlanStats.nrPredicates + "\t");
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
        dataset = args[2];
        switch (args[0]) {
            case "0":
                benchmarkTopK(Integer.parseInt(args[1]));
                break;
            case "1":
                benchmarkResolution(Integer.parseInt(args[1]));
                break;
            case "2":
                benchmarkRows(Integer.parseInt(args[1]));
                break;
            case "3":
                benchmarkWeights(Integer.parseInt(args[1]));
                break;
            case "4":
                benchmarkMerge(Integer.parseInt(args[1]));
                break;
            case "5":
                benchMarkProcessing(Integer.parseInt(args[1]));
                break;
            default:
                String query = "SELECT count(*) FROM sample_311 WHERE \"community_board\"='04 QUEENS'";
                PlanConfig.TOPK = 30;
                PlanConfig.R = 300;
                PlanConfig.PROCESSING_WEIGHT = 0;

                PlanConfig.NR_ROWS = 3;
                runMUVEQuery(query, false);
                runGUROBIQuery(query);
                break;
        }

    }
}
