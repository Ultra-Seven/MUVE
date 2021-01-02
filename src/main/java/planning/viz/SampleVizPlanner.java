package planning.viz;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class SampleVizPlanner {
    public static List<Map<String, List<ScoreDoc>>> plan(ScoreDoc[] hitDocs,
                                                         int nrRows, int R,
                                                         IndexSearcher searcher) throws IOException {
        int nrDocs = hitDocs.length;
        List<Integer>[] docToCtx = new ArrayList[nrDocs];
        int nrAvailable = 2;
        int[] offsets = new int[nrAvailable];
        List<String> literals = new ArrayList<>();
        List<String> columns = new ArrayList<>();

        for (int docCtr = 0; docCtr < nrDocs; docCtr++) {
            docToCtx[docCtr] = new ArrayList<>(nrAvailable);
            ScoreDoc hitScoreDoc = hitDocs[docCtr];
            Document hitDoc = searcher.doc(hitScoreDoc.doc);
            String column = hitDoc.get("column");
            String content = hitDoc.get("text");
            int literalIndex = literals.indexOf(content);
            int columnIndex = columns.indexOf(column);
            if (literalIndex < 0) {
                literalIndex = literals.size();
                literals.add(content);
            }
            if (columnIndex < 0) {
                columnIndex = columns.size();
                columns.add(column);
            }
            docToCtx[docCtr].add(literalIndex);
            docToCtx[docCtr].add(columnIndex);
        }
        offsets[0] = 0;
        offsets[1] = literals.size();
        List<String> plots = Stream.concat(literals.stream(),
                columns.stream()).collect(toList());

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

        }

        return results;
    }

}
