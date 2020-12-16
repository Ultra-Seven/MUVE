package planning.viz;

import de.xypron.linopt.Problem;
import matching.FuzzySearch;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.ScoreDoc;
import org.gnu.glpk.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

import static matching.FuzzySearch.searcher;

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
    public static Map<String, List<ScoreDoc>> plan(ScoreDoc[] hitDocs, int maxFigures) throws IOException {
        // Group by literal values
        Map<String, Map<String, ScoreDoc>> groupByLiterals = new HashMap<>(hitDocs.length);
        Map<String, Set<Integer>> literalsToDocID = new HashMap<>(hitDocs.length);
        // Group by column names
        Map<String, Map<String, ScoreDoc>> groupByColumns = new HashMap<>(hitDocs.length);
        Map<String, Set<Integer>> columnsToDocID = new HashMap<>(hitDocs.length);
        int nrDocs = hitDocs.length;
        for (int docCtr = 0; docCtr < nrDocs; docCtr++) {
            ScoreDoc hitScoreDoc = hitDocs[docCtr];
            Document hitDoc = searcher.doc(hitScoreDoc.doc);
            String column = hitDoc.get("column");
            String content = hitDoc.get("content");

            groupByLiterals.putIfAbsent(content, new HashMap<>());
            Map<String, ScoreDoc> columnToDocs = groupByLiterals.get(content);
            columnToDocs.putIfAbsent(column, hitScoreDoc);
            literalsToDocID.putIfAbsent(content, new HashSet<>());
            literalsToDocID.get(content).add(docCtr);

            groupByColumns.putIfAbsent(column, new HashMap<>());
            Map<String, ScoreDoc> valuesToDocs = groupByColumns.get(column);
            valuesToDocs.putIfAbsent(content, hitScoreDoc);
            columnsToDocID.putIfAbsent(column, new HashSet<>());
            columnsToDocID.get(column).add(docCtr);
        }



        String[] literals = groupByLiterals.keySet().toArray(new String[0]);
        String[] columnNames = groupByColumns.keySet().toArray(new String[0]);

        // Create problem
        glp_prob lp = GLPK.glp_create_prob();
        System.out.println("Problem created");
        GLPK.glp_set_prob_name(lp, "Viz ILP");
        // Define columns
        int nrContexts = groupByColumns.size() + groupByLiterals.size();
        int nrAvailable = 2;
        int nrScopes = nrDocs * nrAvailable;
        GLPK.glp_add_cols(lp, nrScopes + nrContexts);
        int startIndex = 1;
        // Context variables
        for (int contextCtr = 0; contextCtr < nrContexts; contextCtr++) {
            int contextID = contextCtr + startIndex;
            GLPK.glp_set_col_name(lp, contextID, "con_" + contextCtr);
            GLPK.glp_set_col_kind(lp, contextID, GLPKConstants.GLP_BV);
        }
        int[][] docToCtx = new int[nrDocs][nrAvailable];
        startIndex += nrContexts;
        // Document variables
        for (int docCtr = 0; docCtr < nrDocs; docCtr++) {
            ScoreDoc hitScoreDoc = hitDocs[docCtr];
            Document hitDoc = searcher.doc(hitScoreDoc.doc);
            String column_str = hitDoc.get("column");
            String content_str = hitDoc.get("content");

            int literalIndex = Arrays.asList(literals).indexOf(content_str);
            int columnIndex = Arrays.asList(columnNames).indexOf(column_str) + literals.length;
            docToCtx[docCtr][0] = literalIndex;
            docToCtx[docCtr][1] = columnIndex;

            int docIDLiteral = docCtr * nrAvailable + startIndex;
            GLPK.glp_set_col_name(lp, docIDLiteral, "doc_" + docCtr + "_" + literalIndex);
            GLPK.glp_set_col_kind(lp, docIDLiteral, GLPKConstants.GLP_BV);

            int docIDColumn = docCtr * nrAvailable + 1 + startIndex;
            GLPK.glp_set_col_name(lp, docIDColumn, "doc_" + docCtr + "_" + columnIndex);
            GLPK.glp_set_col_kind(lp, docIDColumn, GLPKConstants.GLP_BV);
        }

        // Create constraints
        int nrRows = 1 + nrDocs + nrDocs * nrAvailable;
        GLPK.glp_add_rows(lp, nrRows);
        startIndex = 1;
        // Context constraints
        SWIGTYPE_p_int ind;
        SWIGTYPE_p_double val;
        GLPK.glp_set_row_name(lp, startIndex, "c_" + startIndex);
        GLPK.glp_set_row_bnds(lp, startIndex, GLPKConstants.GLP_FX, 2, 2);
        ind = GLPK.new_intArray(nrContexts + 1);
        val = GLPK.new_doubleArray(nrContexts + 1);
        for (int contextCtr = 0; contextCtr < nrContexts; contextCtr++) {
            GLPK.intArray_setitem(ind, contextCtr + 1, contextCtr + 1);
            GLPK.doubleArray_setitem(val, contextCtr + 1, 1.);
        }
        GLPK.glp_set_mat_row(lp, 1, nrContexts, ind, val);
        startIndex++;

        // Document constraints
        for (int docCtr = 0; docCtr < nrDocs; docCtr++) {
            int rowID = docCtr + startIndex;
            GLPK.glp_set_row_name(lp, rowID, "d_" + docCtr);
            GLPK.glp_set_row_bnds(lp, rowID, GLPKConstants.GLP_UP, 0., 1.);

            for (int contextCtr = 0; contextCtr < nrAvailable; contextCtr++) {
                int docID = docCtr * nrAvailable + contextCtr + nrContexts + 1;
                GLPK.intArray_setitem(ind, contextCtr + 1, docID);
                GLPK.doubleArray_setitem(val, contextCtr + 1, 1.);
            }
            GLPK.glp_set_mat_row(lp, rowID, nrAvailable, ind, val);
        }
        startIndex += nrDocs;

        // Scope constraints
        SWIGTYPE_p_int small_ind = GLPK.new_intArray(3);
        SWIGTYPE_p_double small_val = GLPK.new_doubleArray(3);
        int rowID = startIndex;

        for (int docCtr = 0; docCtr < nrDocs; docCtr++) {
            for (int contextCtr = 0; contextCtr < nrAvailable; contextCtr++) {
                int docID = docCtr * nrAvailable + contextCtr + nrContexts + 1;
                int contextID = docToCtx[docCtr][contextCtr] + 1;
                GLPK.glp_set_row_name(lp, rowID, "s_" + docCtr + "_" + contextCtr);
                GLPK.glp_set_row_bnds(lp, rowID, GLPKConstants.GLP_FX, 0, 0);
                GLPK.intArray_setitem(small_ind, 1, docID);
                GLPK.intArray_setitem(small_ind, 2, contextID);
                GLPK.doubleArray_setitem(small_val, 1, 1.);
                GLPK.doubleArray_setitem(small_val, 2, -1.);
                GLPK.glp_set_mat_row(lp, rowID, 2, small_ind, small_val);
                rowID++;
            }
        }


        // Free memory
        GLPK.delete_intArray(ind);
        GLPK.delete_doubleArray(val);

        GLPK.delete_intArray(small_ind);
        GLPK.delete_doubleArray(small_val);

        // Define objective
        GLPK.glp_set_obj_name(lp, "z");
        GLPK.glp_set_obj_dir(lp, GLPKConstants.GLP_MAX);
        for (int docCtr = 0; docCtr < nrDocs; docCtr++) {
            for (int contextCtr = 0; contextCtr < nrAvailable; contextCtr++) {
                int docID = docCtr * nrAvailable + contextCtr + nrContexts + 1;
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

//        GLPK.glp_write_lp(lp, null, "mipteste.mip");
        GLPK.glp_simplex(lp, simplexParams);
        int ret = GLPK.glp_intopt(lp, iocp);

        // Retrieve solution
        if (ret == 0) {
//            printResults(lp, literals, columnNames);

            Map<String, List<ScoreDoc>> results = new HashMap<>();

            String name = GLPK.glp_get_obj_name(lp);
            double indicator = GLPK.glp_get_obj_val(lp);
            int nrColumns = GLPK.glp_get_num_cols(lp);

            for (int variableCtr = 1; variableCtr <= nrContexts; variableCtr++) {
                name = GLPK.glp_get_col_name(lp, variableCtr);
                indicator = GLPK.glp_mip_col_val(lp, variableCtr);
                if (indicator > Double.MIN_VALUE) {
                    String[] con_arr = name.split("_");
                    int contextCtr = Integer.parseInt(con_arr[1]);
                    String contextName = contextCtr < literals.length ?
                            literals[contextCtr] : columnNames[contextCtr - literals.length];
                    results.put(contextName, new ArrayList<>());

                }
            }

            for (int docCtr = 0; docCtr < nrDocs; docCtr++) {
                for (int contextCtr = 0; contextCtr < nrAvailable; contextCtr++) {
                    int docID = docCtr * nrAvailable + contextCtr + nrContexts + 1;
                    name = GLPK.glp_get_col_name(lp, docID);
                    indicator = GLPK.glp_mip_col_val(lp, docID);

                    if (indicator > Double.MIN_VALUE) {
                        int contextIndex = docToCtx[docCtr][contextCtr];
                        String contextName = contextIndex < literals.length ?
                                literals[contextIndex] : columnNames[contextIndex - literals.length];
                        results.get(contextName).add(hitDocs[docCtr]);
                    }
                }
            }
            System.out.println("");
            for (String groupVal: results.keySet()) {
                System.out.println("Group by: " + groupVal);
                for (ScoreDoc doc: results.get(groupVal)) {
                    Document hitDoc = searcher.doc(doc.doc);
                    String column_str = hitDoc.get("column");
                    String content_str = hitDoc.get("content");
                    System.out.println("Column: " + column_str + "\tParam: " + content_str + "\tScore:" + doc.score);
                }
            }
            // Free memory
            GLPK.glp_delete_prob(lp);
            return results;
        }
        else {
            // Free memory
            GLPK.glp_delete_prob(lp);
            System.out.println("");
            System.out.println("The problem could not be solved");
            return new HashMap<>();
        }
    }


    public static void printResults(glp_prob lp, String[] literals, String[] columnNames) {
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
        for (int i = 1; i <= n; i++) {
            name = GLPK.glp_get_col_name(lp, i);
            indicator = GLPK.glp_mip_col_val(lp, i);
            String[] name_arr = name.split("_");
            if (!lastName.startsWith(name_arr[0]) || !name_arr[1].equals(lastSecond)) {
                System.out.println();
            }
            if (name_arr[0].equals("con")) {
                int contextCtr = Integer.parseInt(name_arr[1]);
                if (contextCtr < literals.length) {
                    name = "Literal: " + literals[contextCtr];
                }
                else {
                    name = "Column: " + columnNames[contextCtr - literals.length];
                }
            }

            if (name_arr[0].equals("doc")) {
                int docCtr = Integer.parseInt(name_arr[1]);
                int contextCtr = Integer.parseInt(name_arr[2]);
                name = "Doc " + docCtr + " ";
                if (contextCtr < literals.length) {
                    name += "Literal: " + literals[contextCtr];
                }
                else {
                    name += "Column: " + columnNames[contextCtr - literals.length];
                }

            }
            System.out.print(name);
            System.out.print(" = ");
            System.out.print(indicator + "\t");
            lastName = name_arr[0];
            lastSecond = name_arr[1];
        }
    }

    public static void main(String[] args) throws IOException, ParseException {
        ScoreDoc[] docs = FuzzySearch.search("brockley");
        plan(docs, 2);
    }
}
