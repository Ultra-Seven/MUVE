package benchmarks;

import config.HostConfig;
import config.PlanConfig;
import connector.PSQLConnector;
import net.sf.jsqlparser.JSQLParserException;
import org.apache.lucene.queryparser.classic.ParseException;
import org.json.JSONArray;
import org.json.JSONObject;
import planning.query.QueryFactory;
import planning.viz.DataPoint;
import planning.viz.Plot;
import planning.viz.PlotGreedyPlanner;
import planning.viz.WaitTimeGurobiPlanner;
import stats.PlanStats;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static benchmarks.PlannerBenchmark.readDatasetQueries;

public class EndToEndBenchmark {
    public final static String DATASET = "delayed_flight";
    private static Connection connection;
    public static long matchMillis = 0;
    public static long optimizeMillis = 0;
    public static long processMillis = 0;
    public static long respondMillis = 0;
    public static long correctMillis = 0;
    public static long endMillis = 0;
    public static double utility = 0;
    public static List<Long> timestamps = new ArrayList<>();
    public static List<Double> distances = new ArrayList<>();
    public static final boolean DYNAMIC = false;
    public static boolean runEndToEndQuery(String query, int sizeRatio) {
        try {
            long matchStart = System.currentTimeMillis();
            QueryFactory queryFactory = new QueryFactory(query);
            long optimizeStart = System.currentTimeMillis();
            matchMillis = optimizeStart - matchStart;
//            List<Map<Plot, List<DataPoint>>> optimalPlan = PlotGreedyPlanner.plan(queryFactory.queries,
//                    queryFactory.nrDistinctValues, PlanConfig.NR_ROWS, PlanConfig.R, queryFactory, false);
            List<Map<Plot, List<DataPoint>>> optimalPlan = WaitTimeGurobiPlanner.plan(queryFactory.queries,
                    queryFactory.nrDistinctValues, PlanConfig.NR_ROWS, PlanConfig.R, queryFactory);
            JSONArray resultRows = new JSONArray();
            long processStart = System.currentTimeMillis();
            optimizeMillis = processStart - optimizeStart;
            PlanConfig.SAMPLING_RATE = sizeRatio;
            for (Map<Plot, List<DataPoint>> plotListMap: optimalPlan) {
                JSONArray resultArray = new JSONArray();
                int sumPixels = plotListMap.values().stream().mapToInt(points ->
                        points.size() * PlanConfig.B + PlanConfig.C).sum();
                for (Plot plot: plotListMap.keySet()) {
                    int pixels = (int) Math.round((plot.nrDataPoints * PlanConfig.B + PlanConfig.C + 0.0) / sumPixels * 100);
                    String mergedQuery = queryFactory.plotQuery(plot, query, true);
                    System.out.println(mergedQuery);
                    Statement statement = connection.createStatement();
                    ResultSet rs = statement.executeQuery(mergedQuery);
                    JSONArray result = new JSONArray();

                    int freeIndex = plot.freeIndex;
                    boolean isLiteral = !queryFactory.valueIndex.contains(freeIndex);
                    int indexPos = Math.max(queryFactory.columnIndex.indexOf(freeIndex),
                            queryFactory.valueIndex.indexOf(freeIndex));
                    int columnIndex = queryFactory.columnIndex.get(indexPos);
                    int valueIndex = queryFactory.valueIndex.get(indexPos);
                    int[] vector = plot.dataPoints.get(0).vector;

                    String groupBy = isLiteral ? queryFactory.keyToTerms[valueIndex][vector[valueIndex]]
                            : queryFactory.keyToTerms[columnIndex][vector[columnIndex]];
                    // A list of highlighted labels
                    List<String> highlightedValues = plot.dataPoints.stream()
                            .filter(x -> x.highlighted)
                            .map(x -> isLiteral ? queryFactory.keyToTerms[columnIndex][x.vector[columnIndex]]
                                    : queryFactory.keyToTerms[valueIndex][x.vector[valueIndex]]).collect(Collectors.toList());
                    if (isLiteral) {
                        boolean hasNext = rs.next();
                        if (hasNext) {
                            for (int columnCtr = 1; columnCtr <= plot.nrDataPoints; columnCtr++) {
                                String value = rs.getString(columnCtr);
                                if (value == null) {
                                    value = "0";
                                }
                                String targetName = queryFactory.keyToTerms[columnIndex]
                                        [plot.dataPoints.get(columnCtr - 1).vector[columnIndex]];
                                JSONObject valueObj = new JSONObject();
                                valueObj.put("highlighted", highlightedValues.contains(targetName))
                                        .put("results", value).put("type", "agg")
                                        .put("label", targetName)
                                        .put("context", "value")
                                        .put("groupby", groupBy);
                                result.put(valueObj);
                            }
                        }
                    }
                    else {
                        while (rs.next()) {
                            String value = rs.getString(1);
                            String targetName = rs.getString(2);
                            JSONObject valueObj = new JSONObject();
                            // Validate number
                            try {
                                value = value.equals("null") ? "0" : value;
                                value = new BigDecimal(value).toPlainString();
                            } catch (Exception ignored) {

                            }
                            valueObj.put("highlighted", highlightedValues.contains(targetName))
                                    .put("results", value).put("type", "agg")
                                    .put("label", targetName)
                                    .put("context", "column")
                                    .put("groupby", groupBy);
                            result.put(valueObj);
                        }
                    }
                    JSONObject plotInformation = new JSONObject();
                    plotInformation.put("data", result);
                    plotInformation.put("width", pixels);

                    resultArray.put(plotInformation);
                }
                resultRows.put(resultArray);
            }
            long processEnd = System.currentTimeMillis();
            processMillis = processEnd - processStart;
            respondMillis = matchMillis + optimizeMillis + processMillis;
            endMillis = respondMillis;
            return true;
        }
        catch (Exception exception) {
            return false;
        }

    }

    public static boolean runIncrementally(String query, int sizeRatio) {
        distances.clear();
        timestamps.clear();
        try {
            long matchStart = System.currentTimeMillis();
            QueryFactory queryFactory = new QueryFactory(query);
            long optimizeStart = System.currentTimeMillis();
            matchMillis = optimizeStart - matchStart;
            List<Map<Plot, List<DataPoint>>> optimalPlan = PlotGreedyPlanner.plan(queryFactory.queries,
                    queryFactory.nrDistinctValues, PlanConfig.NR_ROWS, PlanConfig.R, queryFactory, false);
            long processStart = System.currentTimeMillis();
            optimizeMillis = processStart - optimizeStart;
            List<Plot> highlightedPlots = new ArrayList<>();
            List<Plot> uncoloredPlots = new ArrayList<>();
            JSONObject divInformation = new JSONObject();
            JSONArray divTemplates = new JSONArray();
            int id = 0;
            Map<Plot, String> plotToName = new HashMap<>();
            for (Map<Plot, List<DataPoint>> plotToPoints: optimalPlan) {
                JSONArray highlightedDivs = new JSONArray();
                JSONArray uncoloredDivs = new JSONArray();
                int sumPixels = plotToPoints.values().stream().mapToInt(points ->
                        points.size() * PlanConfig.B + PlanConfig.C).sum();
                for (Plot plot: plotToPoints.keySet()) {
                    String name = "viz_" + id;
                    JSONObject divObj = new JSONObject();
                    divObj.put("name", name);
                    int pixels = (int) Math.round((plot.nrDataPoints * PlanConfig.B + PlanConfig.C + 0.0) / sumPixels * 100);
                    divObj.put("width", pixels);
                    if (plot.nrHighlighted > 0) {
                        highlightedPlots.add(plot);
                        highlightedDivs.put(divObj);
                    }
                    else {
                        uncoloredPlots.add(plot);
                        uncoloredDivs.put(divObj);
                    }
                    id++;
                    plotToName.put(plot, name);
                }
                highlightedDivs.putAll(uncoloredDivs);
                divTemplates.put(highlightedDivs);
            }
            divInformation.put("data", divTemplates);
            // Send div specifications
            highlightedPlots.addAll(uncoloredPlots);
            PlanConfig.SAMPLING_RATE = sizeRatio;
            highlightedPlots.sort(Comparator.comparingInt(o -> o.nrDataPoints));
            double maxProb = 0;
            int correctPlotID = 0;
            for (int plotCtr = 0; plotCtr < highlightedPlots.size(); plotCtr++) {
                Plot plot = highlightedPlots.get(plotCtr);
                for (DataPoint dataPoint: plot.dataPoints) {
                    if (dataPoint.probability > maxProb) {
                        correctPlotID = plotCtr;
                        maxProb = dataPoint.probability;
                    }
                }
            }
            if (highlightedPlots.size() > 1) {
                System.out.println(maxProb);
            }
            for (Plot plot: highlightedPlots) {
                String mergedQuery = queryFactory.plotQuery(plot, query, true);
                System.out.println(mergedQuery);
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(mergedQuery);

                JSONArray result = new JSONArray();
                int freeIndex = plot.freeIndex;
                boolean isLiteral = !queryFactory.valueIndex.contains(freeIndex);
                int indexPos = Math.max(queryFactory.columnIndex.indexOf(freeIndex),
                        queryFactory.valueIndex.indexOf(freeIndex));
                int columnIndex = queryFactory.columnIndex.get(indexPos);
                int valueIndex = queryFactory.valueIndex.get(indexPos);
                int[] vector = plot.dataPoints.get(0).vector;

                String groupBy = isLiteral ? queryFactory.keyToTerms[valueIndex][vector[valueIndex]]
                        : queryFactory.keyToTerms[columnIndex][vector[columnIndex]];
                // A list of highlighted labels
                List<String> highlightedValues = plot.dataPoints.stream()
                        .filter(x -> x.highlighted)
                        .map(x -> isLiteral ? queryFactory.keyToTerms[columnIndex][x.vector[columnIndex]]
                                : queryFactory.keyToTerms[valueIndex][x.vector[valueIndex]]).collect(Collectors.toList());
                if (isLiteral) {
                    boolean hasNext = rs.next();
                    if (hasNext) {
                        for (int columnCtr = 1; columnCtr <= plot.nrDataPoints; columnCtr++) {
                            String value = rs.getString(columnCtr);
                            String targetName = queryFactory.keyToTerms[columnIndex]
                                    [plot.dataPoints.get(columnCtr - 1).vector[columnIndex]];
                            JSONObject valueObj = new JSONObject();
                            valueObj.put("highlighted", highlightedValues.contains(targetName))
                                    .put("results", value).put("type", "agg")
                                    .put("label", targetName)
                                    .put("context", "value")
                                    .put("groupby", groupBy);
                            result.put(valueObj);
                        }
                    }
                }
                else {
                    while (rs.next()) {
                        String value = rs.getString(1);
                        String targetName = rs.getString(2);
                        JSONObject valueObj = new JSONObject();
                        // Validate number
                        try {
                            value = value.equals("null") ? "0" : value;
                            value = new BigDecimal(value).toPlainString();
                        } catch (Exception ignored) {

                        }
                        valueObj.put("highlighted", highlightedValues.contains(targetName))
                                .put("results", value).put("type", "agg")
                                .put("label", targetName)
                                .put("context", "column")
                                .put("groupby", groupBy);
                        result.put(valueObj);
                    }
                }
                JSONObject plotInformation = new JSONObject();
                plotInformation.put("data", result);
                plotInformation.put("name", plotToName.get(plot));
                long timer = System.currentTimeMillis();
                timestamps.add(timer - matchStart);
            }
//            int nrPlots = highlightedPlots.size();
//            for (int distance = nrPlots - 1; distance >= 0; distance--) {
//                distances.add((double) distance / nrPlots);
//            }
            respondMillis = timestamps.get(0);
            processMillis = timestamps.get(timestamps.size() - 1) - optimizeMillis;
            correctMillis = timestamps.get(correctPlotID);
            endMillis = timestamps.get(timestamps.size() - 1);
            System.out.println(Arrays.toString(timestamps.toArray()));
            return true;
        }
        catch (Exception exception) {
            exception.printStackTrace();
            return false;
        }
    }

    public static boolean runApproximately(String query, int sizeRatio) {
        try {
            distances.clear();
            timestamps.clear();
            long matchStart = System.currentTimeMillis();
            QueryFactory queryFactory = new QueryFactory(query);
            long optimizeStart = System.currentTimeMillis();
            matchMillis = optimizeStart - matchStart;
            List<Map<Plot, List<DataPoint>>> optimalPlan = PlotGreedyPlanner.plan(queryFactory.queries,
                    queryFactory.nrDistinctValues, PlanConfig.NR_ROWS, PlanConfig.R, queryFactory, false);
            long samplingStart = System.currentTimeMillis();
            optimizeMillis = samplingStart - optimizeStart;
            List<Plot> highlightedPlots = new ArrayList<>();
            List<Plot> uncoloredPlots = new ArrayList<>();
            JSONObject divInformation = new JSONObject();
            JSONArray divTemplates = new JSONArray();
            int id = 0;
            Map<Plot, String> plotToName = new HashMap<>();
            List<Map<String, Double>> samplingPlots = new ArrayList<>(optimalPlan.size());
            List<Map<String, Double>> processPlots = new ArrayList<>(optimalPlan.size());
            PlanConfig.SAMPLING_RATE = sizeRatio * 5.0 / 100;
            if (DYNAMIC) {
                PlanConfig.SAMPLING_RATE = 1;
                List<String> mergedQueries = new ArrayList<>();
                for (Map<Plot, List<DataPoint>> plotToPoints: optimalPlan) {
                    for (Plot plot: plotToPoints.keySet()) {
                        String mergedQuery = queryFactory.plotQuery(plot, query, true);
                        mergedQueries.add(mergedQuery);
                    }
                }
                String costRegex = "[0-9]+\\.[0-9][0-9]\\.\\.[0-9]+\\.[0-9][0-9]";
                Pattern pattern = Pattern.compile(costRegex);
                List<Double> explainCost = PSQLConnector.getConnector().explain(mergedQueries)
                        .stream().filter(output -> output.charAt(0) != ' ')
                        .mapToDouble(output -> {
                            Matcher matcher = pattern.matcher(output);
                            if (matcher.find()) {
                                String[] numbers = matcher.group(0).split("\\.\\.");
                                return Double.parseDouble(numbers[1]);
                            }
                            else {
                                return 0.0;
                            }
                        }).boxed().collect(Collectors.toList());

                double costs = explainCost.stream().reduce(0.0, Double::sum);
                double time = costs * PlanConfig.PROCESSING_PARAMETER + PlanConfig.BIAS;
                double empiricalRate = PlanConfig.THRESHOLD / time * 1 * 2;
                System.out.println(costs + "\t" + time + "\t" + empiricalRate);
                PlanConfig.SAMPLING_RATE = Math.min(empiricalRate, sizeRatio);
//                PlanConfig.SAMPLING_RATE = sizeRatio;
//                return false;
            }

            for (Map<Plot, List<DataPoint>> plotToPoints: optimalPlan) {
                JSONArray highlightedDivs = new JSONArray();
                JSONArray uncoloredDivs = new JSONArray();
                int sumPixels = plotToPoints.values().stream().mapToInt(points ->
                        points.size() * PlanConfig.B + PlanConfig.C).sum();

                for (Plot plot: plotToPoints.keySet()) {
                    String name = "viz_" + id;
                    JSONObject divObj = new JSONObject();
                    divObj.put("name", name);
                    int pixels = (int) Math.round((plot.nrDataPoints * PlanConfig.B + PlanConfig.C + 0.0) / sumPixels * 100);
                    divObj.put("width", pixels);
                    if (plot.nrHighlighted > 0) {
                        highlightedPlots.add(plot);
                        highlightedDivs.put(divObj);
                    }
                    else {
                        uncoloredPlots.add(plot);
                        uncoloredDivs.put(divObj);
                    }
                    id++;
                    plotToName.put(plot, name);

                    // Sampling data
                    String mergedQuery = queryFactory.plotQuery(plot, query, true);
//                    System.out.println(mergedQuery);
                    Statement statement = connection.createStatement();
                    ResultSet rs = statement.executeQuery(mergedQuery);

                    JSONArray result = new JSONArray();
                    int freeIndex = plot.freeIndex;
                    boolean isLiteral = !queryFactory.valueIndex.contains(freeIndex);
                    int indexPos = Math.max(queryFactory.columnIndex.indexOf(freeIndex),
                            queryFactory.valueIndex.indexOf(freeIndex));
                    int columnIndex = queryFactory.columnIndex.get(indexPos);
                    int valueIndex = queryFactory.valueIndex.get(indexPos);
                    int[] vector = plot.dataPoints.get(0).vector;

                    String groupBy = isLiteral ? queryFactory.keyToTerms[valueIndex][vector[valueIndex]]
                            : queryFactory.keyToTerms[columnIndex][vector[columnIndex]];
                    // A list of highlighted labels
                    List<String> highlightedValues = plot.dataPoints.stream()
                            .filter(x -> x.highlighted)
                            .map(x -> isLiteral ? queryFactory.keyToTerms[columnIndex][x.vector[columnIndex]]
                                    : queryFactory.keyToTerms[valueIndex][x.vector[valueIndex]]).collect(Collectors.toList());
                    Map<String, Double> plotValues = new HashMap<>();
                    if (isLiteral) {
                        boolean hasNext = rs.next();
                        if (hasNext) {
                            for (int columnCtr = 1; columnCtr <= plot.nrDataPoints; columnCtr++) {
                                String value = rs.getString(columnCtr);
                                if (value == null) {
                                    value = "0";
                                }
                                String targetName = queryFactory.keyToTerms[columnIndex]
                                        [plot.dataPoints.get(columnCtr - 1).vector[columnIndex]];
                                JSONObject valueObj = new JSONObject();
                                result.put(valueObj);
                                plotValues.put(targetName, Double.parseDouble(value));
                            }
                        }
                    }
                    else {
                        Set<String> containKeys = new HashSet<>();
                        while (rs.next()) {
                            String value = rs.getString(1);
                            String targetName = rs.getString(2);
                            JSONObject valueObj = new JSONObject();
                            // Validate number
                            try {
                                value = value.equals("null") ? "0" : value;
                                value = new BigDecimal(value).toPlainString();
                            } catch (Exception ignored) {

                            }
                            plotValues.put(targetName, Double.parseDouble(value));
                            containKeys.add(targetName);
                            result.put(valueObj);
                        }
                        plot.dataPoints.stream().map(x -> queryFactory.keyToTerms[valueIndex]
                                [x.vector[valueIndex]])
                                .filter(x -> !containKeys.contains(x))
                                .forEach(x -> result.put(new JSONObject().put("highlighted", highlightedValues.contains(x))
                                        .put("results", 0).put("type", "agg")
                                        .put("label", x)
                                        .put("context", "column")
                                        .put("groupby", groupBy)));
                    }
                    samplingPlots.add(plotValues);
                    divObj.put("result", result);
                }
                highlightedDivs.putAll(uncoloredDivs);
                divTemplates.put(highlightedDivs);
            }
            divInformation.put("data", divTemplates);

            // Send div specifications
            PlanConfig.SAMPLING_RATE = sizeRatio;
            long processStart = System.currentTimeMillis();
//            if (DYNAMIC) {
//                System.out.println(processStart - matchStart);
//                return false;
//            }
            highlightedPlots.addAll(uncoloredPlots);
            for (Plot plot: highlightedPlots) {
                String mergedQuery = queryFactory.plotQuery(plot, query, true);
                System.out.println(mergedQuery);
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(mergedQuery);

                JSONArray result = new JSONArray();
                int freeIndex = plot.freeIndex;
                boolean isLiteral = !queryFactory.valueIndex.contains(freeIndex);
                int indexPos = Math.max(queryFactory.columnIndex.indexOf(freeIndex),
                        queryFactory.valueIndex.indexOf(freeIndex));
                int columnIndex = queryFactory.columnIndex.get(indexPos);
                int valueIndex = queryFactory.valueIndex.get(indexPos);
                int[] vector = plot.dataPoints.get(0).vector;

                String groupBy = isLiteral ? queryFactory.keyToTerms[valueIndex][vector[valueIndex]]
                        : queryFactory.keyToTerms[columnIndex][vector[columnIndex]];
                // A list of highlighted labels
                List<String> highlightedValues = plot.dataPoints.stream()
                        .filter(x -> x.highlighted)
                        .map(x -> isLiteral ? queryFactory.keyToTerms[columnIndex][x.vector[columnIndex]]
                                : queryFactory.keyToTerms[valueIndex][x.vector[valueIndex]]).collect(Collectors.toList());
                Map<String, Double> plotValues = new HashMap<>();
                if (isLiteral) {
                    boolean hasNext = rs.next();
                    if (hasNext) {
                        for (int columnCtr = 1; columnCtr <= plot.nrDataPoints; columnCtr++) {
                            String value = rs.getString(columnCtr);
                            if (value == null) {
                                value = "0";
                            }
                            String targetName = queryFactory.keyToTerms[columnIndex]
                                    [plot.dataPoints.get(columnCtr - 1).vector[columnIndex]];
                            JSONObject valueObj = new JSONObject();
                            valueObj.put("highlighted", highlightedValues.contains(targetName))
                                    .put("results", value).put("type", "agg")
                                    .put("label", targetName)
                                    .put("context", "value")
                                    .put("groupby", groupBy);
                            result.put(valueObj);
                            plotValues.put(targetName, Double.parseDouble(value));
                        }
                    }
                }
                else {
                    while (rs.next()) {
                        String value = rs.getString(1);
                        String targetName = rs.getString(2);
                        JSONObject valueObj = new JSONObject();
                        // Validate number
                        try {
                            value = value.equals("null") ? "0" : value;
                            value = new BigDecimal(value).toPlainString();
                        } catch (Exception ignored) {

                        }
                        valueObj.put("highlighted", highlightedValues.contains(targetName))
                                .put("results", value).put("type", "agg")
                                .put("label", targetName)
                                .put("context", "column")
                                .put("groupby", groupBy);
                        plotValues.put(targetName, Double.parseDouble(value));
                        result.put(valueObj);
                    }
                }
                processPlots.add(plotValues);
                JSONObject plotInformation = new JSONObject();
                plotInformation.put("data", result);
                plotInformation.put("name", plotToName.get(plot));
            }
            long processEnd = System.currentTimeMillis();
            respondMillis = processStart - matchStart;
            processMillis = processEnd - processStart;
            endMillis = processEnd - matchStart;

            double fullUtility = 0;
            double lossUtility = 0;
            for (int plotCtr = 0; plotCtr < processPlots.size(); plotCtr++) {
                Map<String, Double> samplePlot = samplingPlots.get(plotCtr);
                Map<String, Double> fullPlot = processPlots.get(plotCtr);

                for (Map.Entry<String, Double> entry: fullPlot.entrySet()) {
                    String label = entry.getKey();
                    double base = entry.getValue();
                    double value = samplePlot.getOrDefault(label, 0.);
                    double loss = Math.abs(base - value);
                    lossUtility += loss;
                    fullUtility += base;
                }
            }
            utility = (lossUtility / fullUtility);
            return true;
        }
        catch (Exception exception) {
            return false;
        }
    }

    private static boolean runILPBackoff (String query, int sizeRatio) {
        distances.clear();
        timestamps.clear();
        try {
            long matchStart = System.currentTimeMillis();
            QueryFactory queryFactory = new QueryFactory(query);
            double[] timeouts = new double[]{0.05, 0.1, 0.25, 0.5, 1.0};
            long optimizeStart = System.currentTimeMillis();
            matchMillis = optimizeStart - matchStart;
            PlanConfig.SAMPLING_RATE = sizeRatio;
            for (Double timeout: timeouts) {
                PlanConfig.TIMEOUT = timeout;
                List<Map<Plot, List<DataPoint>>> optimalPlan = WaitTimeGurobiPlanner.plan(queryFactory.queries,
                        queryFactory.nrDistinctValues, PlanConfig.NR_ROWS, PlanConfig.R, queryFactory);
                JSONArray resultRows = new JSONArray();
                JSONObject resultObj = new JSONObject();
                for (Map<Plot, List<DataPoint>> plotListMap: optimalPlan) {
                    JSONArray resultArray = new JSONArray();
                    int sumPixels = plotListMap.values().stream().mapToInt(points ->
                            points.size() * PlanConfig.B + PlanConfig.C).sum();
                    for (Plot plot: plotListMap.keySet()) {
                        List<DataPoint> points = plotListMap.get(plot);
                        plot.dataPoints.clear();
                        plot.nrDataPoints = 0;
                        points.forEach(plot::addDataPoint);

                        int nrSize = points.size();
                        int pixels = (int) Math.round((nrSize * PlanConfig.B + PlanConfig.C + 0.0) / sumPixels * 90);
                        String mergedQuery = queryFactory.plotQuery(plot, query, true);
                        System.out.println(mergedQuery);
                        Statement statement = connection.createStatement();
                        ResultSet rs = statement.executeQuery(mergedQuery);
                        JSONArray result = new JSONArray();

                        int freeIndex = plot.freeIndex;
                        boolean isLiteral = !queryFactory.valueIndex.contains(freeIndex) && plot.nrDataPoints > 1;
                        int indexPos = Math.max(queryFactory.columnIndex.indexOf(freeIndex),
                                queryFactory.valueIndex.indexOf(freeIndex));
                        int columnIndex = queryFactory.columnIndex.get(indexPos);
                        int valueIndex = queryFactory.valueIndex.get(indexPos);
                        int[] vector = plot.dataPoints.get(0).vector;

                        String groupBy = isLiteral ? queryFactory.keyToTerms[valueIndex][vector[valueIndex]]
                                : queryFactory.keyToTerms[columnIndex][vector[columnIndex]];
                        // A list of highlighted labels
                        List<String> highlightedValues = plot.dataPoints.stream()
                                .filter(x -> x.highlighted)
                                .map(x -> isLiteral ? queryFactory.keyToTerms[columnIndex][x.vector[columnIndex]]
                                        : queryFactory.keyToTerms[valueIndex][x.vector[valueIndex]]).collect(Collectors.toList());
                        if (isLiteral) {
                            rs.next();
                            for (int columnCtr = 1; columnCtr <= plot.nrDataPoints; columnCtr++) {
                                String value = rs.getString(columnCtr);
                                String targetName = queryFactory.keyToTerms[columnIndex]
                                        [plot.dataPoints.get(columnCtr - 1).vector[columnIndex]];
                                JSONObject valueObj = new JSONObject();
                                valueObj.put("highlighted", highlightedValues.contains(targetName))
                                        .put("results", value).put("type", "agg")
                                        .put("label", targetName)
                                        .put("context", "value")
                                        .put("groupby", groupBy);
                                result.put(valueObj);
                            }
                        }
                        else {
                            while (rs.next()) {
                                String value = rs.getString(1);
                                String targetName = rs.getString(2);
                                JSONObject valueObj = new JSONObject();
                                // Validate number
                                try {
                                    value = value.equals("null") ? "0" : value;
                                    value = new BigDecimal(value).toPlainString();
                                } catch (Exception ignored) {

                                }

                                valueObj.put("highlighted", highlightedValues.contains(targetName))
                                        .put("results", value).put("type", "agg")
                                        .put("label", targetName)
                                        .put("context", "column")
                                        .put("groupby", groupBy);
                                result.put(valueObj);
                            }
                        }
                        JSONObject plotInformation = new JSONObject();
                        plotInformation.put("data", result);
                        plotInformation.put("width", pixels);

                        resultArray.put(plotInformation);

                    }
                    resultRows.put(resultArray);
                }
                resultObj.put("data", resultRows);
                timestamps.add(System.currentTimeMillis() - optimizeStart);
                distances.add(PlanStats.waitTime);
            }
            respondMillis = timestamps.get(0);
            endMillis = timestamps.get(timestamps.size() - 1);
            processMillis = endMillis - timestamps.get(timestamps.size() - 2);
            utility = distances.get(0) - distances.get(timestamps.size() - 1);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    public static void benchmarkDataSize(int sys) throws IOException, SQLException, JSQLParserException, ParseException {
        PrintWriter printWriter;
        PlanConfig.R = 300;
        PlanConfig.TOPK = 20;
        PlanConfig.PROCESSING_WEIGHT = 0.1;
        PlanConfig.NR_ROWS = 1;
        if (sys == 0) {
            printWriter = new PrintWriter("test.csv");
        }
        else if (sys == 1) {
            printWriter = new PrintWriter("test.csv");
        }
        else if (sys == 2) {
            printWriter = new PrintWriter("test.csv");
        }
        else if (sys == 3) {
            printWriter = new PrintWriter("test.csv");
        }
        else {
            printWriter = new PrintWriter("test.csv");
        }
        int[] dataSizes = new int[]{50, 80, 100};
        printWriter.println("Query\tSize\tNrPlots\tNrPredicates\tWaitTime\tMatchMillis" +
                "\tOptimizeMillis\tProcessMillis\tRespondMillis\tEndMillis\tCorrectMillis\tUtility");
        for (int sizeCtr = 0; sizeCtr < dataSizes.length; sizeCtr++) {
            int size = dataSizes[sizeCtr];
            System.out.println("Evaluating Size: " + size);
            int queryCtr = 0;
            List<String> queries = readDatasetQueries(DATASET);
            for(String query: queries) {
                boolean success;
                if (sys == 0) {
                    success = runIncrementally(query, size);
                }
                else if (sys == 1) {
                    success = runApproximately(query, size);
                }
                else if (sys == 2) {
                    success = runApproximately(query, size);
                }
                else if (sys == 3) {
                    success = runILPBackoff(query, size);
                }
                else {
                    success = runEndToEndQuery(query, size);
                }
                queryCtr++;
                if (success) {
                    printWriter.print(queryCtr + "\t");
                    printWriter.print(size + "\t");
                    printWriter.print(PlanStats.nrPlots + "\t");
                    printWriter.print(PlanStats.nrPredicates + "\t");
                    printWriter.print(PlanStats.waitTime + "\t");
                    printWriter.print(matchMillis + "\t");
                    printWriter.print(optimizeMillis + "\t");
                    printWriter.print(processMillis + "\t");
                    printWriter.print(respondMillis + "\t");
                    printWriter.print(endMillis + "\t");
                    printWriter.print(correctMillis + "\t");
                    printWriter.println(utility);
//                    printWriter.print(timestamps.stream().map(String::valueOf).collect(Collectors.joining("|")) + "\t");
//                    printWriter.println(distances.stream().map(String::valueOf).collect(Collectors.joining("|")));
                }
            }
        }
        printWriter.close();
    }

    public static void main(String[] args) throws SQLException, IOException, JSQLParserException, ParseException {
        String url = HostConfig.PG_HOST;
        Properties props = new Properties();
        props.setProperty("user", "postgres");
        props.setProperty("password", "postgres");
        connection = DriverManager.getConnection(url, props);

        benchmarkDataSize(3);
//        List<String> queries = readDatasetQueries(DATASET);
//        for (String query: queries) {
//            runIncrementally(query, 1);
//        }
    }
}
