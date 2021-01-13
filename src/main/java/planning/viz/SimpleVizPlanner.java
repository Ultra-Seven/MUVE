package planning.viz;

import config.PlanConfig;
import matching.FuzzySearch;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.gnu.glpk.*;

import java.io.IOException;
import java.util.*;


/**
 * Optimize the visualization generation by maximizing
 * the overall scores for points rendered in the figures.
 * Assumptions:
 * 1)   each query generates an aggregation value
 * 2)   X figures
 * 3)   two ways to render figures (by params/by column names)
 *
 */
public class SimpleVizPlanner {
    public static List<Map<String, List<ScoreDoc>>> plan(ScoreDoc[] hitDocs,
                                                   int nrRows, int R,
                                                   IndexSearcher searcher) throws IOException {
        int nrDocs = hitDocs.length;
        int nrAvailable = 2;
        int[][] docToCtx = new int[nrDocs][nrAvailable];
        int[] offsets = new int[nrAvailable];
        List<String> literals = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        for (int docCtr = 0; docCtr < nrDocs; docCtr++) {
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
            docToCtx[docCtr][0] = literalIndex;
            docToCtx[docCtr][1] = columnIndex;
        }
        offsets[0] = 0;
        offsets[1] = literals.size();

        // Create problem
        glp_prob lp = GLPK.glp_create_prob();
        System.out.println("Problem created");
        GLPK.glp_set_prob_name(lp, "Viz ILP");
        // Define columns
        int nrContexts = literals.size() + columns.size();

        int nrQueryInRows = nrRows * nrDocs;
        int nrPlotInRows = nrRows * nrContexts;
        GLPK.glp_add_cols(lp, nrQueryInRows + nrPlotInRows);

        int startIndex = 1;
        // Plot variables
        for (int contextCtr = 0; contextCtr < nrContexts; contextCtr++) {
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                int varID = contextCtr * nrRows + rowCtr + startIndex;
                GLPK.glp_set_col_name(lp, varID, "p_" + rowCtr + "_" + contextCtr);
                GLPK.glp_set_col_kind(lp, varID, GLPKConstants.GLP_BV);
            }
        }
        startIndex += nrPlotInRows;

        // Query variables
        for (int docCtr = 0; docCtr < nrDocs; docCtr++) {
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                int varID = docCtr * nrRows + rowCtr + startIndex;
                GLPK.glp_set_col_name(lp, varID, "d_" + rowCtr + "_" + docCtr);
                GLPK.glp_set_col_kind(lp, varID, GLPKConstants.GLP_BV);
            }
        }

        // Create constraints
        int nrConstraints = 1 + nrDocs + nrDocs * nrRows + nrContexts + nrRows;
        GLPK.glp_add_rows(lp, nrConstraints);
        startIndex = 1;
        // Context constraints
        SWIGTYPE_p_int rowInd = GLPK.new_intArray(nrRows + 1);
        SWIGTYPE_p_double rowVal = GLPK.new_doubleArray(nrRows + 1);

        // Constraint 1: Query belongs to at most one row.
        for (int docCtr = 0; docCtr < nrDocs; docCtr++) {
            int constraintID = startIndex + docCtr;
            GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
            GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_UP, 0., 1.);
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                int docID = docCtr * nrRows + rowCtr + nrPlotInRows + 1;
                GLPK.intArray_setitem(rowInd, rowCtr + 1, docID);
                GLPK.doubleArray_setitem(rowVal, rowCtr + 1, 1.);
            }
            GLPK.glp_set_mat_row(lp, constraintID, nrRows, rowInd, rowVal);
        }
        startIndex += nrDocs;

        // Constraint 2: Query will be selected if and only if
        // one of the associated plots is generated.
        SWIGTYPE_p_int avaInd = GLPK.new_intArray(nrAvailable + 2);
        SWIGTYPE_p_double avaVal = GLPK.new_doubleArray(nrAvailable + 2);
        for (int docCtr = 0; docCtr < nrDocs; docCtr++) {
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                int constraintID = docCtr * nrRows + rowCtr + startIndex;
                GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
                GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_LO, 0., 2.);

                for (int contextCtr = 0; contextCtr < nrAvailable; contextCtr++) {
                    int contextID = docToCtx[docCtr][contextCtr] + offsets[contextCtr];
                    int fID = contextID * nrRows + rowCtr + 1;
                    GLPK.intArray_setitem(avaInd, contextCtr + 1, fID);
                    GLPK.doubleArray_setitem(avaVal, contextCtr + 1, 1.);
                }
                int wID = docCtr * nrRows + rowCtr + nrPlotInRows + 1;
                GLPK.intArray_setitem(avaInd, nrAvailable + 1, wID);
                GLPK.doubleArray_setitem(avaVal, nrAvailable + 1, -1.);

                GLPK.glp_set_mat_row(lp, constraintID, nrAvailable + 1, avaInd, avaVal);
            }
        }
        startIndex += nrDocs * nrRows;

        // Constraint 3: Plot can be shown in exactly one row.
        for (int contextCtr = 0; contextCtr < nrContexts; contextCtr++) {
            int constraintID = contextCtr + startIndex;
            GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
            GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_UP, 0., 1.);

            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                int plotID = contextCtr * nrRows + rowCtr + 1;
                GLPK.intArray_setitem(rowInd, rowCtr + 1, plotID);
                GLPK.doubleArray_setitem(rowVal, rowCtr + 1, 1.);
            }
            GLPK.glp_set_mat_row(lp, constraintID, nrRows, rowInd, rowVal);

        }
        startIndex += nrContexts;

        // Constraint 4: Plots in one row cannot exceed the area width.
        SWIGTYPE_p_int widthInd = GLPK.new_intArray(nrDocs + nrContexts + 1);
        SWIGTYPE_p_double widthVal = GLPK.new_doubleArray(nrDocs + nrContexts + 1);
        for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
            int constraintID = rowCtr + startIndex;
            GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
            GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_UP, 0., R);

            // Constant pixels
            for (int contextCtr = 0; contextCtr < nrContexts; contextCtr++) {
                int plotID = contextCtr * nrRows + rowCtr + 1;
                GLPK.intArray_setitem(widthInd, contextCtr + 1, plotID);
                GLPK.doubleArray_setitem(widthVal, contextCtr + 1, PlanConfig.C);
            }
            // Data points pixels
            for (int docCtr = 0; docCtr < nrDocs; docCtr++) {
                int queryID = docCtr * nrRows + rowCtr + nrPlotInRows + 1;
                GLPK.intArray_setitem(widthInd, docCtr + 1 + nrContexts, queryID);
                GLPK.doubleArray_setitem(widthVal, docCtr + 1 + nrContexts, PlanConfig.B);
            }
            GLPK.glp_set_mat_row(lp, constraintID, nrDocs + nrContexts, widthInd, widthVal);
        }

        // Free memory
        GLPK.delete_intArray(rowInd);
        GLPK.delete_doubleArray(rowVal);

        GLPK.delete_intArray(avaInd);
        GLPK.delete_doubleArray(avaVal);

        GLPK.delete_intArray(widthInd);
        GLPK.delete_doubleArray(widthVal);

        // Define objective
        GLPK.glp_set_obj_name(lp, "z");
        GLPK.glp_set_obj_dir(lp, GLPKConstants.GLP_MAX);
        for (int docCtr = 0; docCtr < nrDocs; docCtr++) {
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                int docID = docCtr * nrRows + rowCtr + nrPlotInRows + 1;
                GLPK.glp_set_obj_coef(lp, docID, hitDocs[docCtr].score);
            }
        }

        // Write model to file
        // GLPK.glp_write_lp(lp, null, "lp.lp");

        // Solve model
        glp_iocp iocp = new glp_iocp();

        GLPK.glp_init_iocp(iocp);


        glp_smcp simplexParams = new glp_smcp();

        GLPK.glp_init_smcp(simplexParams);

        GLPK.glp_simplex(lp, simplexParams);
        iocp.setTm_lim(10000);
        simplexParams.setTm_lim(10000);
        int ret = GLPK.glp_intopt(lp, iocp);

        // Retrieve solution
        if (ret == 0) {
//            printResults(lp, literals, columns, docToCtx, nrRows, nrDocs);

            String name;
            double indicator;
            List<Map<String, List<ScoreDoc>>> results = new ArrayList<>();

            int nrLiterals = literals.size();
            int nrColumns = columns.size();
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                Map<String, List<ScoreDoc>> resultsPerRow = new HashMap<>();
                for (int plotCtr = 0; plotCtr < nrContexts; plotCtr++) {
                    int plotID = plotCtr * nrRows + rowCtr + 1;
                    name = GLPK.glp_get_col_name(lp, plotID);
                    indicator = GLPK.glp_mip_col_val(lp, plotID);
                    if (indicator > Double.MIN_VALUE) {
                        String contextName = plotCtr < nrLiterals ?
                                literals.get(plotCtr) : columns.get(plotCtr - nrLiterals);
                        resultsPerRow.put(contextName, new ArrayList<>());
                    }
                }

                for (int docCtr = 0; docCtr < nrDocs; docCtr++) {
                    int docID = docCtr * nrRows + rowCtr + nrPlotInRows + 1;
                    name = GLPK.glp_get_col_name(lp, docID);
                    indicator = GLPK.glp_mip_col_val(lp, docID);

                    if (indicator > Double.MIN_VALUE) {
                        for (int contextCtr = 0; contextCtr < nrAvailable; contextCtr++) {
                            int contextIndex = docToCtx[docCtr][contextCtr] + offsets[contextCtr];
                            String contextName = contextIndex < nrLiterals ?
                                    literals.get(contextIndex) : columns.get(contextIndex - nrLiterals);
                            if (resultsPerRow.containsKey(contextName)) {
                                resultsPerRow.get(contextName).add(hitDocs[docCtr]);
                                break;
                            }
                        }
                    }
                }
                results.add(resultsPerRow);
            }

//            System.out.println("");
//            int rowCtr = 1;
//            for (Map<String, List<ScoreDoc>> resultsPerRow: results) {
//                System.out.println("Row: " + rowCtr);
//                for (String groupVal: resultsPerRow.keySet()) {
//                    System.out.println("Group by: " + groupVal);
//                    for (ScoreDoc doc: resultsPerRow.get(groupVal)) {
//                        Document hitDoc = searcher.doc(doc.doc);
//                        String column_str = hitDoc.get("column");
//                        String content_str = hitDoc.get("text");
//                        System.out.println("Column: " + column_str + "\tParam: " + content_str + "\tScore:" + doc.score);
//                    }
//                }
//                rowCtr++;
//            }
            // Free memory
            GLPK.glp_delete_prob(lp);
            return results;
        }
        else {
            // Free memory
            GLPK.glp_delete_prob(lp);
            System.out.println("");
            System.out.println("The problem could not be solved");
            return new ArrayList<>();
        }
    }

    public static void printResults(glp_prob lp,
                                    List<String> literals,
                                    List<String> columns,
                                    int[][] docToCtx,
                                    int nrRows,
                                    int nrDocs) {
        int n;
        String name;
        String lastName = "";
        String lastSecond = "";
        double indicator;

        name = GLPK.glp_get_obj_name(lp);
        indicator = GLPK.glp_get_obj_val(lp);
        System.out.print(name);
        System.out.print(" = ");
        System.out.println(indicator);
        n = GLPK.glp_get_num_cols(lp);

        int nrLiterals = literals.size();
        int nrColumns = columns.size();
        int nrContexts = nrLiterals + nrColumns;
        int nrAvailable = 2;
        int[] offsets = new int[nrAvailable];
        offsets[0] = 0;
        offsets[1] = nrLiterals;
        for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
            System.out.println("Row " + (rowCtr + 1));
            for (int plotCtr = 0; plotCtr < nrContexts; plotCtr++) {
                int plotID = plotCtr * nrRows + rowCtr + 1;
                name = GLPK.glp_get_col_name(lp, plotID);
                indicator = GLPK.glp_mip_col_val(lp, plotID);
                String contextName = plotCtr < nrLiterals ?
                        literals.get(plotCtr) : columns.get(plotCtr - nrLiterals);
                if (indicator > Double.MIN_VALUE) {
                    System.out.println(contextName + ": " + indicator);
                }
            }

            for (int docCtr = 0; docCtr < nrDocs; docCtr++) {
                int docID = docCtr * nrRows + rowCtr + nrContexts + 1;
                name = GLPK.glp_get_col_name(lp, docID);
                indicator = GLPK.glp_mip_col_val(lp, docID);
                System.out.print("Doc " + docCtr + ":" + indicator + "\t");
                for (int contextCtr = 0; contextCtr < nrAvailable; contextCtr++) {
                    int contextIndex = docToCtx[docCtr][contextCtr] + offsets[contextCtr];
                    int plotID = contextIndex * nrRows + rowCtr + 1;
                    double binary = GLPK.glp_mip_col_val(lp, plotID);
                    String contextName = contextIndex < nrLiterals ?
                            literals.get(contextIndex) : columns.get(contextIndex - nrLiterals);
                    System.out.print("context "  + contextName + ": " + binary + "; ");

                }
                System.out.println("");
            }

        }
    }

    public static void main(String[] args) throws IOException, ParseException {
        String dataset = "sample_311";
        ScoreDoc[] docs = FuzzySearch.search("brockley", dataset);
        plan(docs, 2, PlanConfig.R, FuzzySearch.searchers.get(dataset));
    }
}
