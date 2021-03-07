package planning.viz;

import config.PlanConfig;
import net.sf.jsqlparser.JSQLParserException;
import org.apache.lucene.queryparser.classic.ParseException;
import planning.query.QueryFactory;
import planning.viz.cost.PlanCost;
import stats.PlanStats;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class PlotGreedyPlanner {
    public static List<Map<String, List<DataPoint>>> plan(DataPoint[] scorePoints,
                                                               int[] maxIndices,
                                                               int nrRows, int R,
                                                               QueryFactory factory,
                                                          boolean isStatic) throws IOException, SQLException {
        List<Map<String, List<DataPoint>>> results = new ArrayList<>();
        // Generate a list of data points for query candidates
        int nrQueries = scorePoints.length;
        long startMillis = System.currentTimeMillis();
        Map<Integer, Plot> idToPlots = new HashMap<>(nrQueries);
        int nrDims = scorePoints[0].vector.length;
        int[][] plots = new int[nrQueries][nrDims];
        // Initialize cardinals
        int[] cardinals = new int[nrDims+1];
        cardinals[0] = 1;
        for (int dimCtr = 0; dimCtr < nrDims; dimCtr++) {
            cardinals[dimCtr + 1] = cardinals[dimCtr] * maxIndices[dimCtr];
        }
        for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
            DataPoint dataPoint = scorePoints[queryCtr];
            int[] vector = dataPoint.vector;
            int dataPointID = 0;
            // Calculate data point ID
            for (int valueCtr = 0; valueCtr < nrDims; valueCtr++) {
                dataPointID += cardinals[valueCtr] * vector[valueCtr];
            }
            // Add corresponding plots to the map
            for (int valueCtr = 0; valueCtr < nrDims; valueCtr++) {
                int plotID = dataPointID - cardinals[valueCtr] * vector[valueCtr];
                plots[queryCtr][valueCtr] = plotID;
                if (!idToPlots.containsKey(plotID)) {
                    Plot newPlot = new Plot(plotID, valueCtr);
                    newPlot.addDataPoint(dataPoint);
                    idToPlots.put(plotID, newPlot);
                }
                else {
                    idToPlots.get(plotID).addDataPoint(dataPoint);
                }
            }
        }

        // Remove plots that contain only one query
        int[] removePlots = new int[nrDims];
        for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
            int nrSingles = 0;
            Arrays.fill(removePlots, -1);
            int firstPlotID = plots[queryCtr][0];
            for (int valueCtr = 0; valueCtr < nrDims; valueCtr++) {
                int plotID = plots[queryCtr][valueCtr];
                if (idToPlots.get(plotID).nrDataPoints == 1) {
                    removePlots[valueCtr] = plotID;
                    plots[queryCtr][valueCtr] = -1;
                    nrSingles++;
                }
            }
            if (nrSingles == nrDims) {
                removePlots[0] = -1;
                plots[queryCtr][0] = firstPlotID;
            }
            for (Integer removeID: removePlots) {
                idToPlots.remove(removeID);
            }
        }
        long buildMillis = System.currentTimeMillis();
        int nrPlots = idToPlots.size();
        PlanStats.nrQueries = nrQueries;
        PlanStats.nrPlots = nrPlots;
        int penaltyTime = nrQueries * PlanConfig.READ_DATA + nrPlots * PlanConfig.READ_TITLE;
        if (PlanConfig.PROCESSING_WEIGHT > Double.MIN_VALUE) {
            PlanCost.processCost(idToPlots.values(), factory);
        }
        else {
            for (DataPoint dataPoint: scorePoints) {
                dataPoint.cost = 0;
            }
        }

        Map<Integer, List<Plot>> plotToCandidates = new HashMap<>(nrPlots);
        Set<DataPoint> newDataPoints = new HashSet<>(nrQueries);
        Set<DataPoint> bestDataPoints = new HashSet<>(nrQueries);
        int pixels = 0;

        // Initialize plot candidates
        idToPlots.forEach((plotID, plot) -> {
            plot.sortByProbability();
            List<Plot> plotCandidates = new ArrayList<>(plot.nrDataPoints);
            for (int dataCtr = 0; dataCtr < plot.nrDataPoints; dataCtr++) {
                plotCandidates.add(new Plot(plotID, plot.freeIndex));
            }
            for (int dataCtr = 0; dataCtr < plot.nrDataPoints; dataCtr++) {
                // Add data point into each list
                for (int listCtr = dataCtr; listCtr < plot.nrDataPoints; listCtr++) {
                    plotCandidates.get(listCtr).addDataPoint(plot.dataPoints.get(dataCtr));
                }
            }
            plotToCandidates.put(plotID, plotCandidates);
        });
        List<Plot> bestPlots = new ArrayList<>(nrPlots);
        Map<Integer, Plot> bestPlotsMap = new HashMap<>(nrPlots);
        int nrSubQueries = 0;
        double timeSaving = 0;
        long optimizeMillis = System.currentTimeMillis();
        int offset = 0;
        for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
            double timeSavingForRow = 0;
            while (plotToCandidates.size() > 0) {
                // Remove redundant plots and queries
                if (!isStatic) {
                    selectPlotCandidates(plotToCandidates, newDataPoints, plots, nrDims);
                }
                double bestSavings = Integer.MIN_VALUE;
                Plot bestPlot = null;
                int bestPixels = 0;
                // Find the optimal plot that maximizes the time saving
                for (Map.Entry<Integer, List<Plot>> entry: plotToCandidates.entrySet()) {
                    for (Plot plot: entry.getValue()) {
                        double savings = isStatic ? timeSavingsFromStaticPlot(plot,
                                bestPlots, bestDataPoints, nrSubQueries, penaltyTime) :
                                timeSavingsFromPlot(plot, bestPlots, nrSubQueries, penaltyTime);
                        int plotPixels = PlanConfig.C + PlanConfig.B * plot.nrDataPoints;
                        if (savings > bestSavings && pixels + plotPixels <= R) {
                            bestPlot = plot;
                            bestSavings = savings;
                            bestPixels = plotPixels;
                        }
                    }
                }
                // Find the best plot
                if (bestPlot != null && bestSavings > 0) {
                    bestPlots.add(bestPlot);
                    pixels += bestPixels;
                    timeSavingForRow += bestSavings;
                    if (!isStatic) {
                        nrSubQueries += bestPlot.nrDataPoints;
                        newDataPoints.clear();
                        newDataPoints.addAll(bestPlot.dataPoints);
                        plotToCandidates.get(bestPlot.plotID).remove(bestPlot);
                        mergePlots(bestPlot, bestPlotsMap);
                        bestDataPoints.addAll(bestPlot.dataPoints);
                    }
                    else {
                        nrSubQueries += bestPlot.nrDataPoints;
                        plotToCandidates.get(bestPlot.plotID).remove(bestPlot);
                        bestDataPoints.addAll(bestPlot.dataPoints);
                    }
                }
                else {
                    break;
                }
            }

            timeSaving += timeSavingForRow;
            pixels = 0;
            // Materialize the result of output plan
            Map<String, List<DataPoint>> resultsPerRow = new HashMap<>();
            for (int plotCtr = offset; plotCtr < bestPlots.size(); plotCtr++) {
                Plot bestPlot = bestPlots.get(plotCtr);
                String contextName = String.valueOf(bestPlot.plotID);
                resultsPerRow.put(contextName, bestPlot.dataPoints);
            }
            offset = bestPlots.size();
            results.add(resultsPerRow);
        }

        // Optimize coloring
        int nrBestQueries = bestDataPoints.size();
        int colorCtr = 0;
        int nrHighlightedPlots = 0;
        int nrUncoloredPlots = bestPlots.size();
        double highlightedProbs = 0;
        double uncoloredProbs = bestDataPoints.stream()
                .reduce(0.0, (partialResult, dataPoint) -> partialResult + dataPoint.probability, Double::sum);
        while (colorCtr < nrBestQueries / 2) {
            DataPoint bestDataPoint = null;
            double bestTime = Integer.MAX_VALUE;
            boolean newHighlight = false;
            boolean removeUncolored = false;
            double queryProbs = 0;
            for (Plot plot: bestPlots) {
                boolean queryNewHighlight = plot.nrHighlighted == 0;
                boolean queryRemoveUncolored = plot.nrHighlighted == plot.nrDataPoints - 1;
                for (DataPoint dataPoint: plot.dataPoints) {
                    if (!dataPoint.highlighted) {
                        double timeFromColoring = timeFromColoring(dataPoint,
                                queryNewHighlight ? nrHighlightedPlots + 1 : nrHighlightedPlots,
                                queryRemoveUncolored ? nrUncoloredPlots - 1 : nrUncoloredPlots,
                                colorCtr + 1,
                                nrBestQueries - colorCtr - 1,
                                highlightedProbs,
                                uncoloredProbs,
                                penaltyTime);
                        if (timeFromColoring < bestTime) {
                            bestDataPoint = dataPoint;
                            bestTime = timeFromColoring;
                            newHighlight = queryNewHighlight;
                            removeUncolored = queryRemoveUncolored;
                            queryProbs = dataPoint.probability;
                        }
                    }
                }
            }
            double bestSaving = penaltyTime - bestTime;
            if (bestDataPoint != null && bestSaving > timeSaving) {
                timeSaving = bestSaving;
                bestDataPoint.highlighted = true;
                if (newHighlight) {
                    nrHighlightedPlots++;
                }
                if (removeUncolored) {
                    nrUncoloredPlots--;
                }
                highlightedProbs += queryProbs;
            }
            else {
                break;
            }
            colorCtr++;
        }
        long endMillis = System.currentTimeMillis();
        PlanStats.initMillis = buildMillis - startMillis;
        PlanStats.buildMillis = optimizeMillis - buildMillis;
        PlanStats.optimizeMillis = endMillis - optimizeMillis;
        PlanStats.isTimeout = false;
        PlanStats.waitTime = penaltyTime - timeSaving;

        System.out.println("Cost: " + PlanStats.waitTime);

        int rowCtr = 0;
        for (Map<String, List<DataPoint>> resultsPerRow: results) {
            System.out.println("Row: " + rowCtr);
            for (String groupVal: resultsPerRow.keySet()) {
                System.out.println("Group by: " + groupVal);
                for (DataPoint dataPoint: resultsPerRow.get(groupVal)) {
                    System.out.println(factory.queryString(dataPoint) +
                            "\tScore:" + dataPoint.probability + "\tHighlighted: " + dataPoint.highlighted);
                }
            }
            rowCtr++;
        }

        return results;
    }

    /**
     * Calculates time savings when highlighting queries.
     *
     * @param dataPoint                 The data point to be highlighted
     * @param nrHighlightedPlots        Number of highlighted plots
     * @param nrUncoloredPlots          Number of uncolored plots
     * @return                          The time savings from outputting matching queries within plot
     */
    private static double timeFromColoring(DataPoint dataPoint,
                                           int nrHighlightedPlots,
                                           int nrUncoloredPlots,
                                           int nrHighlightedQueries,
                                           int nrUncoloredQueries,
                                           double highlightProbs,
                                           double uncoloredProbes,
                                           int penaltyTime) {
        int timeForHighlighted = (nrHighlightedPlots * PlanConfig.READ_TITLE
                + nrHighlightedQueries * PlanConfig.READ_DATA);

        int timeForUncolored = (nrUncoloredPlots * PlanConfig.READ_TITLE
                + nrUncoloredQueries * PlanConfig.READ_DATA);

        double time = (highlightProbs + dataPoint.probability) * 0.5 * timeForHighlighted
                + (uncoloredProbes - dataPoint.probability) * (timeForHighlighted + 0.5 * timeForUncolored)
                + (1 - highlightProbs - uncoloredProbes) * penaltyTime;
        return time;
    }

    /**
     * Calculates time savings when outputting queries in the context of plot.
     *
     * @param plot          The plot used to save time in outputting rows
     * @param bestPlots     Set of optimal plots observed so far
     * @param nrSubQueries  Number of queries in optimal plots
     * @return              The time savings from outputting matching queries within plot
     */
    private static double timeSavingsFromPlot(Plot plot, List<Plot> bestPlots, int nrSubQueries, int penalty) {
        // Additional time cost for reading queries in the new plot
        int timeForQueries = plot.nrDataPoints * PlanConfig.READ_DATA;
        // Additional time cost for reading the title of new plot
        int timeForTitle = PlanConfig.READ_TITLE;

        double totalSavings = 0;
        for (Plot selectedPlot: bestPlots) {
            totalSavings -= (selectedPlot.probability * 0.5 * (timeForTitle + timeForQueries));
        }

        double cost = PlanConfig.PROCESSING_WEIGHT * plot.cost * plot.nrDataPoints
                + 0.5 * ((nrSubQueries + plot.nrDataPoints) * PlanConfig.READ_DATA +
                (1 + bestPlots.size()) * PlanConfig.READ_TITLE);

        double plotTimeSaving = penalty - cost;

        totalSavings += (plot.probability * plotTimeSaving);

        return totalSavings;
    }

    /**
     * Calculates time savings when outputting queries in the context of plot.
     *
     * @param plot          The plot used to save time in outputting rows
     * @param bestPlots     Set of optimal plots observed so far
     * @param nrSubQueries  Number of queries in optimal plots
     * @return              The time savings from outputting matching queries within plot
     */
    private static double timeSavingsFromStaticPlot(Plot plot, List<Plot> bestPlots,
                                                    Set<DataPoint> bestDataPoints, int nrSubQueries, int penalty) {

        List<DataPoint> distinctDataPoints = new ArrayList<>(plot.nrDataPoints);
        distinctDataPoints.addAll(plot.dataPoints);
        distinctDataPoints.removeAll(bestDataPoints);
        int nrPlotQueries = distinctDataPoints.size();
        double probability = distinctDataPoints.stream()
                .reduce(0.0, (partialResult, dataPoint) -> partialResult + dataPoint.probability, Double::sum);

        // Additional time cost for reading queries in the new plot
        int timeForQueries = nrPlotQueries * PlanConfig.READ_DATA;
        // Additional time cost for reading the title of new plot
        int timeForTitle = PlanConfig.READ_TITLE;

        double totalSavings = 0;
        for (Plot selectedPlot: bestPlots) {
            totalSavings -= (selectedPlot.probability * 0.5 * (timeForTitle + timeForQueries));
        }

        double cost = PlanConfig.PROCESSING_WEIGHT * plot.cost * nrPlotQueries
                + 0.5 * ((nrSubQueries + plot.nrDataPoints) * PlanConfig.READ_DATA +
                (1 + bestPlots.size()) * PlanConfig.READ_TITLE);

        double plotTimeSaving = penalty - cost;
        totalSavings += (probability * plotTimeSaving);

        return totalSavings;
    }

    /**
     * Remove some queries and plots from plot candidate
     * based on some observations.
     *
     * @param plotToCandidates
     * @param newDataPoints
     * @param plots
     * @param nrDims
     */
    public static void selectPlotCandidates(Map<Integer, List<Plot>> plotToCandidates,
                                            Set<DataPoint> newDataPoints,
                                            int[][] plots, int nrDims) {
        // Remove queries from plot candidates
        newDataPoints.forEach(dataPoint -> {
            for (int dimCtr = 0; dimCtr < nrDims; dimCtr++) {
                int plotID = plots[dataPoint.id][dimCtr];
                if (plotID >= 0 && plotToCandidates.containsKey(plotID)) {
                    plotToCandidates.get(plotID).forEach(plot -> plot.removeDataPoint(dataPoint));
                }
            }
        });

        if (newDataPoints.size() > 0) {
            // Remove redundant plots
            plotToCandidates.forEach((plotID, candidates) -> {
                Map<Integer, List<Plot>> plotMap = new HashMap<>(candidates.size());
                Iterator<Plot> plotIterator = candidates.iterator();
                while (plotIterator.hasNext()) {
                    Plot nextPlot = plotIterator.next();
                    int nrDataPoints = nextPlot.nrDataPoints;
                    if (plotMap.containsKey(nrDataPoints)) {
                        for (Plot plot: plotMap.get(nrDataPoints)) {
                            if (nextPlot.equals(plot)) {
                                plotIterator.remove();
                            }
                            break;
                        }
                    }
                    else {
                        plotMap.putIfAbsent(nrDataPoints, new ArrayList<>());
                        plotMap.get(nrDataPoints).add(nextPlot);
                    }
                }
            });
        }

    }

    /**
     * Merge plots with the same ID together
     *
     */
    private static void mergePlots(Plot bestPlot, Map<Integer, Plot> plotMap) {
        int plotID = bestPlot.plotID;
        if (plotMap.containsKey(plotID)) {
            for (DataPoint dataPoint: bestPlot.dataPoints) {
                Plot savedPlot = plotMap.get(bestPlot.plotID);
                savedPlot.addDataPoint(dataPoint);
                savedPlot.sortByProbability();
            }
        }
        else {
            plotMap.put(plotID, bestPlot);
        }
    }


    public static void main(String[] args) throws IOException, ParseException, JSQLParserException, SQLException {
        String query = "SELECT count(*) FROM dob_job WHERE \"city\" = 'Bronx' and \"city\"=\"borough\";";
        QueryFactory queryFactory = new QueryFactory(query);

        plan(queryFactory.queries, queryFactory.nrDistinctValues, 2, PlanConfig.R, queryFactory, true);

    }
}
