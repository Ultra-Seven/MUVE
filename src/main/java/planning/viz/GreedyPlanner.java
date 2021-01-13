package planning.viz;

import config.PlanConfig;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class GreedyPlanner {
    public static List<Map<String, List<ScoreDoc>>> plan(ScoreDoc[] hitDocs,
                                                         int nrRows, int R,
                                                         IndexSearcher searcher) throws IOException {
        Arrays.sort(hitDocs, (doc1, doc2) -> Double.compare(doc2.score, doc1.score));
        int nrDocs = hitDocs.length;
        // Calculate average rewards for each plot.
        List<Integer>[] docToCtx = new ArrayList[nrDocs];
        int nrAvailable = 2;
        int[] offsets = new int[nrAvailable];
        List<String> literals = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Map<String, Double> plotsToRewards = new HashMap<>();
        Map<String, Integer> plotsToNumbers = new HashMap<>();

        for (int docCtr = 0; docCtr < nrDocs; docCtr++) {
            docToCtx[docCtr] = new ArrayList<>(nrAvailable);
            ScoreDoc hitScoreDoc = hitDocs[docCtr];
            Document hitDoc = searcher.doc(hitScoreDoc.doc);
            String column = hitDoc.get("column");
            String content = hitDoc.get("text");
            double score = hitScoreDoc.score;
            int literalIndex = literals.indexOf(content);
            int columnIndex = columns.indexOf(column);
            if (literalIndex < 0) {
                literalIndex = literals.size();
                literals.add(content);
                plotsToRewards.put(content, 0.0);
                plotsToNumbers.put(content, 0);
            }
            if (columnIndex < 0) {
                columnIndex = columns.size();
                columns.add(column);
                plotsToRewards.put(column, 0.0);
                plotsToNumbers.put(column, 0);
            }
            docToCtx[docCtr].add(literalIndex);
            docToCtx[docCtr].add(columnIndex);
            plotsToRewards.computeIfPresent(content, (key, value) -> value + score);
            plotsToRewards.computeIfPresent(column, (key, value) -> value + score);
            plotsToNumbers.computeIfPresent(content, (key, value) -> value + 1);
            plotsToNumbers.computeIfPresent(column, (key, value) -> value + 1);
        }
        offsets[0] = 0;
        offsets[1] = literals.size();
        List<String> plots = Stream.concat(literals.stream(),
                columns.stream()).collect(toList());
        // Penalize the plots that have less bars
        plotsToRewards.replaceAll((k, v) -> v / (plotsToNumbers.get(k) + 1));

        // Assign documents to plots
        List<Map<String, List<ScoreDoc>>> results = new ArrayList<>();
        int width = 0;
        int rowCtr = 0;
        List<Set<Integer>> includedPlots = new ArrayList<>(nrRows);
        for (int row = 0; row < nrRows; row++) {
            includedPlots.add(new HashSet<>());
            results.add(new HashMap<>());
        }
        Set<Integer> plotsForRow = includedPlots.get(rowCtr);
        Map<String, List<ScoreDoc>> resultsForRow = results.get(rowCtr);
        for (int docCtr = 0; docCtr < nrDocs; docCtr++) {
            ScoreDoc hitScoreDoc = hitDocs[docCtr];
            List<Integer> admissiblePlots = docToCtx[docCtr];
            // Find a optimal plot
            String optimalName = "";
            double optimalScore = 0;
            int optimalIndex = 0;
            for (int plotCtr = 0; plotCtr < admissiblePlots.size(); plotCtr++) {
                int plotIndex = admissiblePlots.get(plotCtr) + offsets[plotCtr];
                String plotName = plots.get(plotIndex);
                double score = plotsToRewards.get(plotName);
                if (score > optimalScore) {
                    optimalScore = score;
                    optimalName = plotName;
                    optimalIndex = plotIndex;
                }
            }
            boolean newPlot = !plotsForRow.contains(optimalIndex);
            int appendPixels = newPlot ? PlanConfig.B + PlanConfig.C:
                    PlanConfig.B;
            if (width + appendPixels < R) {
                width += appendPixels;
                // Add the pair (query, plot) to the result map.
                if (newPlot) {
                    resultsForRow.put(optimalName, new ArrayList<>());
                    plotsForRow.add(optimalIndex);
                }
                resultsForRow.get(optimalName).add(hitScoreDoc);
            }
            else {
                if (rowCtr == nrRows - 1) {
                    break;
                }
                rowCtr++;
                width = 0;
                plotsForRow = includedPlots.get(rowCtr);
                resultsForRow = results.get(rowCtr);
            }
        }
        return results;
    }
}
