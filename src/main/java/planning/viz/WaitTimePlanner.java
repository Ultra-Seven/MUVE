package planning.viz;

import config.PlanConfig;
import net.sf.jsqlparser.JSQLParserException;
import org.apache.lucene.queryparser.classic.ParseException;
import org.gnu.glpk.*;
import planning.query.QueryFactory;
import planning.viz.cost.PlanCost;
import stats.PlanStats;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * Different from the SimpleVizPlanner that maximizes
 * the probability of plots shown in the screen, this
 * model tends to find the plan to minimize the wait
 * time. We consider two time models: duration of cognitive
 * time and query processing time. We solve the problem
 * by integer linear programming.
 *
 * @author Ziyun Wei
 */
public class WaitTimePlanner {
    /**
     * Generate the optimal plan by ILP to minimize
     * the wait time.
     *
     * @param scorePoints           array of similar data points
     * @param maxIndices            number of distinct candidates for each replaceable terms
     * @param nrRows                number of rows in the screen
     * @param R                     width of a row
     * @param factory               similar queries generator
     *
     * @return                      the optimal plan
     * @throws IOException
     * @throws SQLException
     */
    public static List<Map<String, List<DataPoint>>> plan(DataPoint[] scorePoints,
                                                          int[] maxIndices,
                                                          int nrRows, int R,
                                                          QueryFactory factory) throws IOException, SQLException {
        List<Map<String, List<DataPoint>>> results = new ArrayList<>();
        // Generate a list of data points for query candidates
        int nrQueries = scorePoints.length;
        Map<Integer, Plot> idToPlots = new HashMap<>(nrQueries);
        int nrDims = scorePoints[0].vector.length;
        int[][] plots = new int[nrQueries][nrDims];
        long startMillis = System.currentTimeMillis();
        // Initialize cardinals
        int[] cardinals = new int[nrDims+1];
        cardinals[0] = 1;

        for (int dimCtr = 0; dimCtr < nrDims; dimCtr++) {
            cardinals[dimCtr + 1] = cardinals[dimCtr] * maxIndices[dimCtr];
        }
        int maxQueries = 0;
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
                    maxQueries = Math.max(maxQueries, newPlot.nrDataPoints);
                }
                else {
                    Plot curPlot = idToPlots.get(plotID);
                    curPlot.addDataPoint(dataPoint);
                    maxQueries = Math.max(maxQueries, curPlot.nrDataPoints);
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

        // Initialize processing overhead from Postgres
        int nrPlots = idToPlots.size();
        PlanStats.nrQueries = nrQueries;
        PlanStats.nrPlots = nrPlots;
        PlanCost.processCost(idToPlots.values(), factory);

        // Sort the number of plot based on probability
        // Build a index mapping query to plots
        int maxPlot = 0;
        int maxPlotsForQuery = 0;
        Map<Integer, Integer> plotIDToVarID = new HashMap<>(nrPlots);
        int[][] queriesToPlots = new int[nrQueries][nrDims+1];
        int[][] plotsToQueries = new int[nrPlots][maxQueries+1];
        int[] queriesPlotOffsets = new int[nrQueries];
        int plotOffsets = 0;
        for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
            int queryNrPlots = 0;
            int offset = 1;
            for (int valueCtr = 0; valueCtr < nrDims; valueCtr++) {
                int plotID = plots[queryCtr][valueCtr];
                if (plotID >= 0) {
                    if (!plotIDToVarID.containsKey(plotID)) {
                        int nextID = plotIDToVarID.size();
                        plotIDToVarID.put(plotID, nextID);
                    }
                    int variableID = plotIDToVarID.get(plotID);
                    maxPlot = Math.max(maxPlot, variableID);
                    queriesToPlots[queryCtr][offset] = variableID;

                    // Add to plot index
                    int nrQueriesInPlot = plotsToQueries[variableID][0];
                    plotsToQueries[variableID][nrQueriesInPlot+1] = queryCtr;
                    plotsToQueries[variableID][0]++;

                    queryNrPlots++;
                    offset++;
                }
            }
            maxPlotsForQuery = Math.max(maxPlotsForQuery, queryNrPlots);
            queriesToPlots[queryCtr][0] = queryNrPlots;
            queriesPlotOffsets[queryCtr] = plotOffsets;
            plotOffsets += queryNrPlots;
        }
        long buildMillis = System.currentTimeMillis();
        // Create problem
        glp_prob lp = GLPK.glp_create_prob();
        System.out.println("Problem created");
        GLPK.glp_set_prob_name(lp, "Viz Wait Time ILP");
        // Define columns
        int nrPlotInRows = nrRows * nrPlots;
        int nrQueryInPlotsInRows = Arrays.stream(queriesToPlots).reduce(0,
                (result, array)->result + array[0],Integer::sum) * nrRows;
        int nrProducts = nrQueries * 2;
//        int nrProducts = nrQueries * 3 * (nrQueries + nrPlots);
        int nrPlotsForm = nrPlots * 2;
        GLPK.glp_add_cols(lp, nrPlotInRows + nrQueryInPlotsInRows * 2 + nrProducts + nrPlotsForm);

        int startIndex = 1;
        // Plot variables
        for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                int varID = plotCtr * nrRows + rowCtr + startIndex;
                GLPK.glp_set_col_name(lp, varID, "p_" + rowCtr + "_" + plotCtr);
                GLPK.glp_set_col_kind(lp, varID, GLPKConstants.GLP_BV);
            }
        }
        startIndex += nrPlotInRows;

        // Highlighted query variables
        for (int queryCtr = 0; queryCtr < nrQueryInPlotsInRows; queryCtr++) {
            int varID = queryCtr + startIndex;
            GLPK.glp_set_col_name(lp, varID, "h_" + queryCtr);
            GLPK.glp_set_col_kind(lp, varID, GLPKConstants.GLP_BV);
        }

        startIndex += nrQueryInPlotsInRows;

        // Uncolored query variables
        for (int queryCtr = 0; queryCtr < nrQueryInPlotsInRows; queryCtr++) {
            int varID = queryCtr + startIndex;
            GLPK.glp_set_col_name(lp, varID, "u_" + queryCtr);
            GLPK.glp_set_col_kind(lp, varID, GLPKConstants.GLP_BV);
        }

        startIndex += nrQueryInPlotsInRows;

        // Product variables
        int nrQueryInPlots = nrQueryInPlotsInRows / nrRows;
        for (int formCtr = 0; formCtr < 2; formCtr++) {
            for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
                int varID = formCtr * nrQueries + queryCtr + startIndex;
                GLPK.glp_set_col_name(lp, varID, "product_" + formCtr + "_" + queryCtr);
                GLPK.glp_set_col_kind(lp, varID, GLPKConstants.GLP_IV);
                GLPK.glp_set_col_bnds(lp, varID, GLPKConstants.GLP_LO, 0., 0.);
            }
        }
//        for (int productCtr = 0; productCtr < 3; productCtr++) {
//            for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
//                for (int binaryCtr = 0; binaryCtr < nrQueries + nrPlots; binaryCtr++) {
//                    int varID = productCtr * nrQueries * (nrQueries + nrPlots) + queryCtr * (nrQueries + nrPlots) +
//                            binaryCtr + startIndex;
//                    GLPK.glp_set_col_name(lp, varID, "product_" + productCtr + "_" + queryCtr);
//                    GLPK.glp_set_col_kind(lp, varID, GLPKConstants.GLP_BV);
//                }
//            }
//        }
        startIndex += nrProducts;

        // Plot form
        for (int formCtr = 0; formCtr < 2; formCtr++) {
            for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
                int varID = formCtr * nrPlots + plotCtr + startIndex;
                GLPK.glp_set_col_name(lp, varID, "form_" + plotCtr);
                GLPK.glp_set_col_kind(lp, varID, GLPKConstants.GLP_BV);
            }
        }

        startIndex += nrPlotsForm;

//        // Unary tasks variables
//        for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
//            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
//                int varID = queryCtr * nrRows + rowCtr + startIndex;
//                GLPK.glp_set_col_name(lp, varID, "u_" + rowCtr + "_" + queryCtr);
//                GLPK.glp_set_col_kind(lp, varID, GLPKConstants.GLP_BV);
//            }
//        }
//
//        // Join tasks variables
//        for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
//            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
//                int varID = queryCtr * nrRows + rowCtr + startIndex;
//                GLPK.glp_set_col_name(lp, varID, "j_" + rowCtr + "_" + queryCtr);
//                GLPK.glp_set_col_kind(lp, varID, GLPKConstants.GLP_BV);
//            }
//        }

        // Create constraints
        int nrConstraints = nrQueries + nrQueryInPlots
                + nrPlots + nrRows + nrPlotsForm * 2 + 1 + nrProducts * 4;
        GLPK.glp_add_rows(lp, nrConstraints);
        startIndex = 1;
        // Context constraints
        SWIGTYPE_p_int rowInd = GLPK.new_intArray(maxPlotsForQuery * nrRows * 2 + 1);
        SWIGTYPE_p_double rowVal = GLPK.new_doubleArray(maxPlotsForQuery * nrRows * 2 + 1);

        // Constraint 1: Query belongs to at most one row.
        for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
            SWIGTYPE_p_int localRowInd = GLPK.new_intArray(maxPlotsForQuery * nrRows * 2 + 1);
            SWIGTYPE_p_double localRowVal = GLPK.new_doubleArray(maxPlotsForQuery * nrRows * 2 + 1);
            int constraintID = startIndex + queryCtr;
            GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
            GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_UP, 0., 1.);
            int[] plotsForQuery = queriesToPlots[queryCtr];
            int nrPlotsForQuery = plotsForQuery[0];
            int offset = queriesPlotOffsets[queryCtr];
            int index = 0;
            for (int formCtr = 0; formCtr < 2; formCtr++) {
                for (int queryPlotCtr = 1; queryPlotCtr <= nrPlotsForQuery; queryPlotCtr++) {
                    for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                        int varID = (queryPlotCtr + offset - 1) * nrRows +
                                rowCtr + formCtr * nrQueryInPlotsInRows + nrPlotInRows + 1;
                        GLPK.intArray_setitem(localRowInd, index + 1, varID);
                        GLPK.doubleArray_setitem(localRowVal, index + 1, 1.);
                        index++;
                    }
                }
            }
            GLPK.glp_set_mat_row(lp, constraintID, nrPlotsForQuery * nrRows * 2, localRowInd, localRowVal);
            GLPK.delete_intArray(localRowInd);
            GLPK.delete_doubleArray(localRowVal);
        }
        startIndex += nrQueries;

        // Constraint 2: Query will be selected if and only if
        // one of the associated plots is generated.
        for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
            int queryNrPlots = queriesToPlots[queryCtr][0];
            int offset = queriesPlotOffsets[queryCtr];
            for (int plotCtr = 1; plotCtr <= queryNrPlots; plotCtr++) {
                int localSize = 3 * nrRows + 1;
                SWIGTYPE_p_int localRowInd = GLPK.new_intArray(localSize);
                SWIGTYPE_p_double localRowVal = GLPK.new_doubleArray(localSize);
                int constraintID = plotCtr - 1 + offset + startIndex;
                GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
                GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_LO, 0., 1.);
                int index = 0;
                // Sum of exist indicator for plots
                for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                    int plotID = queriesToPlots[queryCtr][plotCtr];
                    int wID = plotID * nrRows + rowCtr + 1;
                    GLPK.intArray_setitem(localRowInd, index + 1, wID);
                    GLPK.doubleArray_setitem(localRowVal, index + 1, 1.);
                    index++;
                }
                // Query in a row
                for (int formCtr = 0; formCtr < 2; formCtr++) {
                    for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                        int varID = (plotCtr + offset - 1) * nrRows +
                                rowCtr + formCtr * nrQueryInPlotsInRows + nrPlotInRows + 1;
                        GLPK.intArray_setitem(localRowInd, index + 1, varID);
                        GLPK.doubleArray_setitem(localRowVal, index + 1, -1.);
                        index++;
                    }
                }
                GLPK.glp_set_mat_row(lp, constraintID, index, localRowInd, localRowVal);
                GLPK.delete_intArray(localRowInd);
                GLPK.delete_doubleArray(localRowVal);
            }
        }
        startIndex += nrQueryInPlots;

        // Constraint 3: Plot can be shown in exactly one row.
        for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
            int constraintID = plotCtr + startIndex;
            int localSize = nrRows + 1;
            SWIGTYPE_p_int localRowInd = GLPK.new_intArray(localSize);
            SWIGTYPE_p_double localRowVal = GLPK.new_doubleArray(localSize);
            GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
            GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_UP, 0., 1.);

            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                int plotID = plotCtr * nrRows + rowCtr + 1;
                GLPK.intArray_setitem(localRowInd, rowCtr + 1, plotID);
                GLPK.doubleArray_setitem(localRowVal, rowCtr + 1, 1.);
            }
            GLPK.glp_set_mat_row(lp, constraintID, nrRows, localRowInd, localRowVal);

            GLPK.delete_intArray(localRowInd);
            GLPK.delete_doubleArray(localRowVal);

        }
        startIndex += nrPlots;

        // Constraint 4: Plots in one row cannot exceed the area width.
        SWIGTYPE_p_int widthInd = GLPK.new_intArray(2 * nrQueryInPlots + nrPlots + 1);
        SWIGTYPE_p_double widthVal = GLPK.new_doubleArray(2 * nrQueryInPlots + nrPlots + 1);
        for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
            int constraintID = rowCtr + startIndex;
            GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
            GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_UP, 0., R);

            // Constant pixels
            for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
                int plotID = plotCtr * nrRows + rowCtr + 1;
                GLPK.intArray_setitem(widthInd, plotCtr + 1, plotID);
                GLPK.doubleArray_setitem(widthVal, plotCtr + 1, PlanConfig.C);
            }
            // Data points pixels
            for (int queryCtr = 0; queryCtr < nrQueryInPlots; queryCtr++) {
                int queryID = queryCtr * nrRows + rowCtr + nrPlotInRows + 1;
                GLPK.intArray_setitem(widthInd, queryCtr + 1 + nrPlots, queryID);
                GLPK.doubleArray_setitem(widthVal, queryCtr + 1 + nrPlots, PlanConfig.B);
                GLPK.intArray_setitem(widthInd, queryCtr + 1 + nrQueryInPlots + nrPlots,
                        queryID + nrQueryInPlotsInRows);
                GLPK.doubleArray_setitem(widthVal, queryCtr + 1 + nrQueryInPlots + nrPlots, PlanConfig.B);
            }
            GLPK.glp_set_mat_row(lp, constraintID, nrQueryInPlots * 2 + nrPlots, widthInd, widthVal);

            GLPK.delete_intArray(widthInd);
            GLPK.delete_doubleArray(widthVal);
            widthInd = GLPK.new_intArray(2 * nrQueryInPlots + nrPlots + 1);
            widthVal = GLPK.new_doubleArray(2 * nrQueryInPlots + nrPlots + 1);
        }

        startIndex += nrRows;

        // Constraint 5: At most half of queries are highlighted.
        SWIGTYPE_p_int allInd = GLPK.new_intArray(nrQueryInPlotsInRows * 2 + 1);
        SWIGTYPE_p_double allVal = GLPK.new_doubleArray(nrQueryInPlotsInRows * 2 + 1);
        GLPK.glp_set_row_name(lp, startIndex, "c_" + startIndex);
        GLPK.glp_set_row_bnds(lp, startIndex, GLPKConstants.GLP_UP, 0., 0.);
        for (int queryCtr = 0; queryCtr < nrQueryInPlots; queryCtr++) {
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                int queryID = queryCtr * nrRows + rowCtr + nrPlotInRows + 1;
                int index = queryCtr * nrRows + rowCtr + 1;
                GLPK.intArray_setitem(allInd, index, queryID);
                GLPK.doubleArray_setitem(allVal, index, 1);
            }
        }
        for (int queryCtr = 0; queryCtr < nrQueryInPlots; queryCtr++) {
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                int queryID = queryCtr * nrRows + rowCtr + nrPlotInRows + nrQueryInPlotsInRows + 1;
                int index = queryCtr * nrRows + rowCtr + 1 + nrQueryInPlotsInRows;
                GLPK.intArray_setitem(allInd, index, queryID);
                GLPK.doubleArray_setitem(allVal, index, -1);
            }
        }
        GLPK.glp_set_mat_row(lp, startIndex, nrQueryInPlotsInRows * 2, allInd, allVal);
        GLPK.delete_intArray(allInd);
        GLPK.delete_doubleArray(allVal);
        startIndex++;

        int largeSize = maxPlotsForQuery * nrRows + nrQueryInPlotsInRows + nrPlots + 2;
        SWIGTYPE_p_int largeInd = GLPK.new_intArray(largeSize);
        SWIGTYPE_p_double largeVal = GLPK.new_doubleArray(largeSize);
        // Constraint 6: Specify whether plot contains highlighted and uncolored queries.
        for (int formCtr = 0; formCtr < 2; formCtr++) {
            for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
                // highlighted/uncolored plot indicator upperbound
                int constraintID = (formCtr * nrPlots + plotCtr) * 2 + startIndex;

                GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
                GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_UP, 0., 0.);

                int index = 0;
                int hPlotID = formCtr * nrPlots + plotCtr + nrProducts + nrQueryInPlotsInRows * 2 + nrPlotInRows + 1;
                GLPK.intArray_setitem(largeInd, index + 1, hPlotID);
                GLPK.doubleArray_setitem(largeVal, index + 1, 1.);
                index++;
                int nrQueriesInPlot = plotsToQueries[plotCtr][0];
                int[] variableIDs = new int[nrQueriesInPlot * nrRows];
                for (int innerQueryCtr = 1; innerQueryCtr <= nrQueriesInPlot; innerQueryCtr++) {
                    int plotCtrInQueryIndex = -1;
                    int queryCtr = plotsToQueries[plotCtr][innerQueryCtr];
                    int nrPlotsInQuery = queriesToPlots[queryCtr][0];
                    for (int plotCtrInQuery = 1; plotCtrInQuery <= nrPlotsInQuery; plotCtrInQuery++) {
                        if (queriesToPlots[queryCtr][plotCtrInQuery] == plotCtr) {
                            plotCtrInQueryIndex = plotCtrInQuery - 1;
                            break;
                        }
                    }
                    for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                        int queryID = formCtr * nrQueryInPlotsInRows + (queriesPlotOffsets[queryCtr] +
                                plotCtrInQueryIndex) * nrRows + rowCtr + nrPlotInRows + 1;
                        variableIDs[index - 1] = queryID;
                        GLPK.intArray_setitem(largeInd, index + 1, queryID);
                        GLPK.doubleArray_setitem(largeVal, index + 1, -1);
                        index++;
                    }
                }
                GLPK.glp_set_mat_row(lp, constraintID, index, largeInd, largeVal);
                GLPK.delete_intArray(largeInd);
                GLPK.delete_doubleArray(largeVal);
                largeInd = GLPK.new_intArray(largeSize);
                largeVal = GLPK.new_doubleArray(largeSize);

                // highlighted/uncolored plot indicator lower bound
                constraintID = constraintID + 1;
                GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
                GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_LO, 0., 1.);

                index = 0;
                GLPK.intArray_setitem(largeInd, index + 1, hPlotID);
                GLPK.doubleArray_setitem(largeVal, index + 1, nrQueriesInPlot);
                index++;

                for (int variableCtr = 0; variableCtr < variableIDs.length; variableCtr++) {
                    int queryID = variableIDs[variableCtr];
                    GLPK.intArray_setitem(largeInd, index + 1, queryID);
                    GLPK.doubleArray_setitem(largeVal, index + 1, -1.);
                    index++;
                }
                GLPK.glp_set_mat_row(lp, constraintID, index, largeInd, largeVal);
                GLPK.delete_intArray(largeInd);
                GLPK.delete_doubleArray(largeVal);
                largeInd = GLPK.new_intArray(largeSize);
                largeVal = GLPK.new_doubleArray(largeSize);
            }
        }

        startIndex += (2 * nrPlotsForm);

        // Constraint 7: Product of query variable and penalty
//        int readTime = (PlanConfig.READ_DATA * nrQueries + PlanConfig.READ_TITLE * nrPlots) * 2;
//        int readTime = PlanConfig.PENALTY_TIME;

        int[] formOffsets = new int[]{0, nrQueryInPlotsInRows, nrQueryInPlotsInRows};
        int[] rightQueryFormOffsets = new int[]{0, 0, nrQueryInPlotsInRows};
        int[] rightPlotFormOffsets = new int[]{0, 0, nrPlots};
//        for (int formCtr = 0; formCtr < 3; formCtr++) {
//            int formOffset = formOffsets[formCtr];
//            int rightQueryFormOffset = rightQueryFormOffsets[formCtr];
//            int rightPlotFormOffset = rightPlotFormOffsets[formCtr];
//            for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
//                for (int rightCtr = 0; rightCtr < nrQueries + nrPlots; rightCtr++) {
//                    // case 1 for highlighted queries: x1 - y >= 0
//                    int constraintID = (formCtr * nrQueries * (nrQueries + nrPlots)
//                            + queryCtr * (nrQueries + nrPlots) + rightCtr) * 3 + startIndex;
//
//                    int productID = formCtr * nrQueries * (nrQueries + nrPlots) + queryCtr * (nrQueries + nrPlots)
//                            + rightCtr + nrQueryInPlotsInRows * 2 + nrPlotInRows + 1;
//                    GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
//                    GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_LO, 0., 1.);
//
//                    int[] plotsForQuery = queriesToPlots[queryCtr];
//                    int nrPlotsForQuery = plotsForQuery[0];
//                    int offset = queriesPlotOffsets[queryCtr];
//                    int index = 0;
//                    int localSize = nrPlotsForQuery * nrRows + 2;
//                    SWIGTYPE_p_int localRowInd = GLPK.new_intArray(localSize);
//                    SWIGTYPE_p_double localRowVal = GLPK.new_doubleArray(localSize);
//                    List<String> ids = new ArrayList<>();
//                    for (int queryPlotCtr = 1; queryPlotCtr <= nrPlotsForQuery; queryPlotCtr++) {
//                        for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
//                            int varID = (queryPlotCtr + offset - 1) * nrRows +
//                                    rowCtr + nrPlotInRows + 1 + formOffset;
//                            ids.add(idToString(varID, queriesToPlots, plotsToQueries, nrRows, nrQueryInPlotsInRows, nrProducts));
//                            GLPK.intArray_setitem(localRowInd, index + 1, varID);
//                            GLPK.doubleArray_setitem(localRowVal, index + 1, 1.);
//                            index++;
//                        }
//                    }
//
//                    GLPK.intArray_setitem(localRowInd, index + 1, productID);
//                    GLPK.doubleArray_setitem(localRowVal, index + 1, -1);
//                    index++;
//                    GLPK.glp_set_mat_row(lp, constraintID, index, localRowInd, localRowVal);
//                    GLPK.delete_intArray(localRowInd);
//                    GLPK.delete_doubleArray(localRowVal);
//                    index = 0;
//
//
//                    // case 2 for plots: x2 - y >= 0
//                    constraintID++;
//                    GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
//                    GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_LO, 0., 1.0);
//
//                    localSize = maxPlotsForQuery * nrRows + 2;
//                    localRowInd = GLPK.new_intArray(localSize);
//                    localRowVal = GLPK.new_doubleArray(localSize);
//                    ids.clear();
//                    if (rightCtr < nrQueries) {
//                        int[] plotsForRightQuery = queriesToPlots[rightCtr];
//                        int nrPlotsForRightQuery = plotsForRightQuery[0];
//                        int rightOffset = queriesPlotOffsets[rightCtr];
//                        for (int rightQueryPlotCtr = 1; rightQueryPlotCtr <= nrPlotsForRightQuery; rightQueryPlotCtr++) {
//                            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
//                                int varID = (rightQueryPlotCtr + rightOffset - 1) * nrRows +
//                                        rowCtr + nrPlotInRows + 1 + rightQueryFormOffset;
//                                ids.add(idToString(varID, queriesToPlots, plotsToQueries, nrRows, nrQueryInPlotsInRows, nrProducts));
//                                GLPK.intArray_setitem(localRowInd, index + 1, varID);
//                                GLPK.doubleArray_setitem(localRowVal, index + 1, 1.);
//                                index++;
//                            }
//                        }
//                    }
//                    else {
//                        int rightPlotCtr = rightCtr - nrQueries;
//                        int varID = rightPlotCtr + nrProducts + nrQueryInPlotsInRows * 2
//                                + nrPlotInRows + 1 + rightPlotFormOffset;
//                        ids.add(idToString(varID, queriesToPlots, plotsToQueries, nrRows, nrQueryInPlotsInRows, nrProducts));
//                        GLPK.intArray_setitem(localRowInd, index + 1, varID);
//                        GLPK.doubleArray_setitem(localRowVal, index + 1, 1.);
//                        index++;
//                    }
//
//                    GLPK.intArray_setitem(localRowInd, index + 1, productID);
//                    GLPK.doubleArray_setitem(localRowVal, index + 1, -1);
//                    index++;
//                    GLPK.glp_set_mat_row(lp, constraintID, index, localRowInd, localRowVal);
//                    GLPK.delete_intArray(localRowInd);
//                    GLPK.delete_doubleArray(localRowVal);
//                    index = 0;
//
//                    // case 3 for plots: x1 + x2 - y <= 1
//                    constraintID++;
//                    localSize = maxPlotsForQuery * nrRows * 2 + 2;
//                    localRowInd = GLPK.new_intArray(localSize);
//                    localRowVal = GLPK.new_doubleArray(localSize);
//                    GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
//                    GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_UP, 0., 1.);
//
//                    int constant = rightCtr == queryCtr && formCtr != 1 ? 2 : 1;
//                    ids.clear();
//                    for (int queryPlotCtr = 1; queryPlotCtr <= nrPlotsForQuery; queryPlotCtr++) {
//                        for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
//                            int varID = (queryPlotCtr + offset - 1) * nrRows +
//                                    rowCtr + nrPlotInRows + 1 + formOffset;
//                            ids.add(idToString(varID, queriesToPlots, plotsToQueries, nrRows, nrQueryInPlotsInRows, nrProducts));
//                            GLPK.intArray_setitem(localRowInd, index + 1, varID);
//                            GLPK.doubleArray_setitem(localRowVal, index + 1, constant);
//                            index++;
//                        }
//                    }
//                    if (rightCtr == queryCtr && formCtr != 1) {
//
//                    }
//                    else if (rightCtr < nrQueries) {
//                        int[] plotsForRightQuery = queriesToPlots[rightCtr];
//                        int nrPlotsForRightQuery = plotsForRightQuery[0];
//                        int rightOffset = queriesPlotOffsets[rightCtr];
//                        for (int rightQueryPlotCtr = 1; rightQueryPlotCtr <= nrPlotsForRightQuery; rightQueryPlotCtr++) {
//                            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
//                                int varID = (rightQueryPlotCtr + rightOffset - 1) * nrRows +
//                                        rowCtr + nrPlotInRows + 1 + rightQueryFormOffset;
//                                ids.add(idToString(varID, queriesToPlots, plotsToQueries, nrRows, nrQueryInPlotsInRows, nrProducts));
//                                GLPK.intArray_setitem(localRowInd, index + 1, varID);
//                                GLPK.doubleArray_setitem(localRowVal, index + 1, 1.);
//                                index++;
//                            }
//                        }
//                    }
//                    else {
//                        int rightPlotCtr = rightCtr - nrQueries;
//                        int varID = rightPlotCtr + nrProducts + nrQueryInPlotsInRows * 2
//                                + nrPlotInRows + 1 + rightPlotFormOffset;
//                        ids.add(idToString(varID, queriesToPlots, plotsToQueries, nrRows, nrQueryInPlotsInRows, nrProducts));
//                        GLPK.intArray_setitem(localRowInd, index + 1, varID);
//                        GLPK.doubleArray_setitem(localRowVal, index + 1, 1.);
//                        index++;
//                    }
//
//                    GLPK.intArray_setitem(localRowInd, index + 1, productID);
//                    GLPK.doubleArray_setitem(localRowVal, index + 1, -1);
//                    index++;
//                    GLPK.glp_set_mat_row(lp, constraintID, index, localRowInd, localRowVal);
//                    GLPK.delete_intArray(localRowInd);
//                    GLPK.delete_doubleArray(localRowVal);
//
//                }
//            }
//        }

        int largeM = (nrQueries * PlanConfig.READ_DATA + nrPlots * PlanConfig.READ_TITLE);
        int readTime = largeM;
        for (int formCtr = 0; formCtr < 2; formCtr++) {
            int formOffset = formOffsets[formCtr];
            for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
                // case 1 for highlighted queries: M >= DR + Mq - y
                int constraintID = (formCtr * nrQueries + queryCtr) * 4 + startIndex;
                int productID = formCtr * nrQueries + queryCtr + nrQueryInPlotsInRows * 2 + nrPlotInRows + 1;
                GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
                GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_UP, 0., largeM);

                int index = 0;
                int nrPlotsForQuery = queriesToPlots[queryCtr][0];
                int localSize = formCtr == 0 ? nrQueryInPlotsInRows + nrPlots + 2 :
                        (nrQueryInPlotsInRows + nrPlots) * 2 + 2;
                int offset = queriesPlotOffsets[queryCtr];

                SWIGTYPE_p_int localRowInd = GLPK.new_intArray(localSize);
                SWIGTYPE_p_double localRowVal = GLPK.new_doubleArray(localSize);
                double expectedConstant = formCtr == 0 ? 0.5 : 1;
                int min = offset * nrRows + nrPlotInRows + 1 + formOffset;
                int max = offset * nrRows + nrPlotsForQuery * nrRows + nrPlotInRows + 1 + formOffset;
                for (int queryCombineCtr = 0; queryCombineCtr < nrQueryInPlotsInRows; queryCombineCtr++) {
                    int varID = queryCombineCtr + nrPlotInRows + 1;
                    GLPK.intArray_setitem(localRowInd, index + 1, varID);
                    double constant = varID >= min && varID < max ?
                            expectedConstant * PlanConfig.READ_DATA + largeM :
                            expectedConstant * PlanConfig.READ_DATA;
//                    System.out.println(idToString(varID, queriesToPlots, plotsToQueries, nrRows, nrQueryInPlotsInRows, nrProducts));
//                    System.out.println(constant);
                    GLPK.doubleArray_setitem(localRowVal, index + 1, constant);
                    index++;
                }

                if (formCtr == 1) {
                    for (int queryCombineCtr = 0; queryCombineCtr < nrQueryInPlotsInRows; queryCombineCtr++) {
                        int varID = queryCombineCtr + nrPlotInRows + 1 + nrQueryInPlotsInRows;
                        double constant = varID >= min && varID < max ?
                                0.5 * PlanConfig.READ_DATA + largeM :
                                0.5 * PlanConfig.READ_DATA;
//                        System.out.println(idToString(varID, queriesToPlots, plotsToQueries, nrRows, nrQueryInPlotsInRows, nrProducts));
//                        System.out.println(constant);
                        GLPK.intArray_setitem(localRowInd, index + 1, varID);
                        GLPK.doubleArray_setitem(localRowVal, index + 1, constant);
                        index++;
                    }
                }

                for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
                    int varID = plotCtr + nrProducts + nrQueryInPlotsInRows * 2 + nrPlotInRows + 1;
                    GLPK.intArray_setitem(localRowInd, index + 1, varID);
                    GLPK.doubleArray_setitem(localRowVal, index + 1,
                            expectedConstant * PlanConfig.READ_TITLE);
//                    System.out.println(idToString(varID, queriesToPlots, plotsToQueries, nrRows, nrQueryInPlotsInRows, nrProducts));
//                    System.out.println(expectedConstant * PlanConfig.READ_TITLE);
                    index++;
                }

                if (formCtr == 1) {
                    for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
                        int varID = plotCtr + nrProducts + nrQueryInPlotsInRows * 2 + nrPlotInRows + 1 + nrPlots;
                        GLPK.intArray_setitem(localRowInd, index + 1, varID);
                        GLPK.doubleArray_setitem(localRowVal, index + 1,
                                0.5 * PlanConfig.READ_TITLE);
//                        System.out.println(idToString(varID, queriesToPlots, plotsToQueries, nrRows, nrQueryInPlotsInRows, nrProducts));
//                        System.out.println(0.5 * PlanConfig.READ_TITLE);
                        index++;
                    }
                }

                GLPK.intArray_setitem(localRowInd, index + 1, productID);
                GLPK.doubleArray_setitem(localRowVal, index + 1, -1.);
                index++;

                GLPK.glp_set_mat_row(lp, constraintID, index, localRowInd, localRowVal);
                GLPK.delete_intArray(localRowInd);
                GLPK.delete_doubleArray(localRowVal);
                index = 0;


                // case 2 for plots: y - DR + Mq <= M
                constraintID++;
                localRowInd = GLPK.new_intArray(localSize);
                localRowVal = GLPK.new_doubleArray(localSize);
                GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
                GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_UP, 0, largeM);
                GLPK.intArray_setitem(localRowInd, index + 1, productID);
                GLPK.doubleArray_setitem(localRowVal, index + 1, 1.);
                index++;
                for (int queryCombineCtr = 0; queryCombineCtr < nrQueryInPlotsInRows; queryCombineCtr++) {
                    int varID = queryCombineCtr + nrPlotInRows + 1;
                    double constant = varID >= min && varID < max ?
                            -1 * expectedConstant * PlanConfig.READ_DATA + largeM :
                            -1 * expectedConstant * PlanConfig.READ_DATA;
//                    System.out.println(idToString(varID, queriesToPlots, plotsToQueries, nrRows, nrQueryInPlotsInRows, nrProducts));
//                    System.out.println(constant);
                    GLPK.intArray_setitem(localRowInd, index + 1, varID);
                    GLPK.doubleArray_setitem(localRowVal, index + 1,
                            constant);
                    index++;
                }

                if (formCtr == 1) {
                    for (int queryCombineCtr = 0; queryCombineCtr < nrQueryInPlotsInRows; queryCombineCtr++) {
                        int varID = queryCombineCtr + nrPlotInRows + 1 + nrQueryInPlotsInRows;
                        double constant = varID >= min && varID < max ?
                                -0.5 * PlanConfig.READ_DATA + largeM :
                                -0.5 * PlanConfig.READ_DATA;
//                        System.out.println(idToString(varID, queriesToPlots, plotsToQueries, nrRows, nrQueryInPlotsInRows, nrProducts));
//                        System.out.println(constant);
                        GLPK.intArray_setitem(localRowInd, index + 1, varID);
                        GLPK.doubleArray_setitem(localRowVal, index + 1, constant);
                        index++;
                    }
                }

                for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
                    int varID = plotCtr + nrProducts + nrQueryInPlotsInRows * 2 + nrPlotInRows + 1;
                    GLPK.intArray_setitem(localRowInd, index + 1, varID);
                    GLPK.doubleArray_setitem(localRowVal, index + 1,
                            -1 * expectedConstant * PlanConfig.READ_TITLE);
//                    System.out.println(idToString(varID, queriesToPlots, plotsToQueries, nrRows, nrQueryInPlotsInRows, nrProducts));
//                    System.out.println(-1 * expectedConstant * PlanConfig.READ_TITLE);
                    index++;
                }

                if (formCtr == 1) {
                    for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
                        int varID = plotCtr + nrProducts + nrQueryInPlotsInRows * 2 + nrPlotInRows + 1 + nrPlots;
                        GLPK.intArray_setitem(localRowInd, index + 1, varID);
                        GLPK.doubleArray_setitem(localRowVal, index + 1,
                                -0.5 * PlanConfig.READ_TITLE);
//                        System.out.println(idToString(varID, queriesToPlots, plotsToQueries, nrRows, nrQueryInPlotsInRows, nrProducts));
//                        System.out.println(-0.5 * PlanConfig.READ_TITLE);
                        index++;
                    }
                }

                GLPK.glp_set_mat_row(lp, constraintID, index, localRowInd, localRowVal);
                GLPK.delete_intArray(localRowInd);
                GLPK.delete_doubleArray(localRowVal);
                index = 0;

                // case 3 for plots: y + Mq >= 0
                constraintID++;
                localSize = nrPlotsForQuery * nrRows + 2;
                localRowInd = GLPK.new_intArray(localSize);
                localRowVal = GLPK.new_doubleArray(localSize);
                GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
                GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_LO, 0., 0.);

                GLPK.intArray_setitem(localRowInd, index + 1, productID);
                GLPK.doubleArray_setitem(localRowVal, index + 1, 1.);
                index++;

                for (int queryPlotCtr = 1; queryPlotCtr <= nrPlotsForQuery; queryPlotCtr++) {
                    for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                        int varID = (queryPlotCtr + offset - 1) * nrRows +
                                rowCtr + nrPlotInRows + 1 + formOffset;
                        GLPK.intArray_setitem(localRowInd, index + 1, varID);
                        GLPK.doubleArray_setitem(localRowVal, index + 1, largeM);
//                        System.out.println(idToString(varID, queriesToPlots, plotsToQueries, nrRows, nrQueryInPlotsInRows, nrProducts));
//                        System.out.println(largeM);
                        index++;
                    }
                }
                GLPK.glp_set_mat_row(lp, constraintID, index, localRowInd, localRowVal);
                GLPK.delete_intArray(localRowInd);
                GLPK.delete_doubleArray(localRowVal);
                index = 0;

                // case 4 for plots: y - Mq <= 0
                constraintID++;
                localSize = nrPlotsForQuery * nrRows + 2;
                localRowInd = GLPK.new_intArray(localSize);
                localRowVal = GLPK.new_doubleArray(localSize);
                GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
                GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_UP, 0., 0.);

                GLPK.intArray_setitem(localRowInd, index + 1, productID);
                GLPK.doubleArray_setitem(localRowVal, index + 1, 1.);
                index++;

                for (int queryPlotCtr = 1; queryPlotCtr <= nrPlotsForQuery; queryPlotCtr++) {
                    for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                        int varID = (queryPlotCtr + offset - 1) * nrRows +
                                rowCtr + nrPlotInRows + 1 + formOffset;
                        GLPK.intArray_setitem(localRowInd, index + 1, varID);
                        GLPK.doubleArray_setitem(localRowVal, index + 1, -1 * largeM);
//                        System.out.println(idToString(varID, queriesToPlots, plotsToQueries, nrRows, nrQueryInPlotsInRows, nrProducts));
//                        System.out.println(-1 * largeM);
                        index++;
                    }
                }
                GLPK.glp_set_mat_row(lp, constraintID, index, localRowInd, localRowVal);
                GLPK.delete_intArray(localRowInd);
                GLPK.delete_doubleArray(localRowVal);
            }
        }


        // Free memory
        GLPK.delete_intArray(rowInd);
        GLPK.delete_doubleArray(rowVal);

        GLPK.delete_intArray(largeInd);
        GLPK.delete_doubleArray(largeVal);

        GLPK.delete_intArray(widthInd);
        GLPK.delete_doubleArray(widthVal);

        // Define objective
        GLPK.glp_set_obj_name(lp, "z");
        GLPK.glp_set_obj_dir(lp, GLPKConstants.GLP_MIN);


        for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
            double probability = scorePoints[queryCtr].probability;
            double cost = scorePoints[queryCtr].cost;

            int nrPlotsForQuery = queriesToPlots[queryCtr][0];
            for (int plotCtr = 1; plotCtr <= nrPlotsForQuery; plotCtr++) {
                int variableIndex = queriesPlotOffsets[queryCtr] + plotCtr - 1;
                for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                    int highlightID = variableIndex * nrRows + rowCtr + nrPlotInRows + 1;
                    int uncoloredID = variableIndex * nrRows + rowCtr + nrQueryInPlotsInRows + nrPlotInRows + 1;
                    double coefficient = (-1 * readTime + PlanConfig.PROCESSING_WEIGHT * cost) * probability;
                    GLPK.glp_set_obj_coef(lp, highlightID, coefficient);
                    GLPK.glp_set_obj_coef(lp, uncoloredID, coefficient);
                }
            }
        }

        for (int formCtr = 0; formCtr < 2; formCtr++) {
            for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
                double probability = scorePoints[queryCtr].probability;
                int productID = formCtr * nrQueries + queryCtr +
                        + nrQueryInPlotsInRows * 2 + nrPlotInRows + 1;
                GLPK.glp_set_obj_coef(lp, productID, probability);
            }
        }

        long optimizeMillis = System.currentTimeMillis();
        // Solve the model
        glp_iocp iocp = new glp_iocp();
        GLPK.glp_init_iocp(iocp);
        glp_smcp simplexParams = new glp_smcp();
        GLPK.glp_init_smcp(simplexParams);
        GLPK.glp_simplex(lp, simplexParams);
        // Set time out
        iocp.setTm_lim(PlanConfig.TIME_OUT);
        simplexParams.setTm_lim(PlanConfig.TIME_OUT);
        int ret = GLPK.glp_intopt(lp, iocp);

        // Retrieve solution
        if (ret == 0) {
            String name;
            double indicator;
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                Map<String, List<DataPoint>> resultsPerRow = new HashMap<>();
                for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
                    int plotID = plotCtr * nrRows + rowCtr + 1;
                    name = GLPK.glp_get_col_name(lp, plotID);
                    indicator = GLPK.glp_mip_col_val(lp, plotID);
                    if (indicator > Double.MIN_VALUE) {
                        String contextName = String.valueOf(plotID);
                        resultsPerRow.put(contextName, new ArrayList<>());
                    }
                }
                results.add(resultsPerRow);
            }

            // Highlighted queries
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                Map<String, List<DataPoint>> resultsPerRow = results.get(rowCtr);
                for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
                    int nrPlotsForQuery = queriesToPlots[queryCtr][0];
                    for (int plotCtr = 1; plotCtr <= nrPlotsForQuery; plotCtr++) {
                        int variableIndex = queriesPlotOffsets[queryCtr] + plotCtr - 1;
                        int queryID = variableIndex * nrRows + rowCtr + nrPlotInRows + 1;
                        name = GLPK.glp_get_col_name(lp, queryID);
                        indicator = GLPK.glp_mip_col_val(lp, queryID);

                        if (indicator > Double.MIN_VALUE) {
                            int plotIndex = queriesToPlots[queryCtr][plotCtr];
                            int plotID = plotIndex * nrRows + rowCtr + 1;
                            String contextName = String.valueOf(plotID);
                            if (resultsPerRow.containsKey(contextName)) {
                                scorePoints[queryCtr].highlighted = true;
                                resultsPerRow.get(contextName).add(scorePoints[queryCtr]);
                            }
                            else {
                                System.out.println("Wrong here!");
                            }
                        }
                    }
                }
                // Uncolored queries
                for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
                    int nrPlotsForQuery = queriesToPlots[queryCtr][0];
                    for (int plotCtr = 1; plotCtr <= nrPlotsForQuery; plotCtr++) {
                        int variableIndex = queriesPlotOffsets[queryCtr] + plotCtr - 1;
                        int queryID = variableIndex * nrRows + rowCtr + nrQueryInPlotsInRows + nrPlotInRows + 1;
                        name = GLPK.glp_get_col_name(lp, queryID);
                        indicator = GLPK.glp_mip_col_val(lp, queryID);

                        if (indicator > Double.MIN_VALUE) {
                            int plotIndex = queriesToPlots[queryCtr][plotCtr];
                            int plotID = plotIndex * nrRows + rowCtr + 1;
                            String contextName = String.valueOf(plotID);
                            if (resultsPerRow.containsKey(contextName)) {
                                resultsPerRow.get(contextName).add(scorePoints[queryCtr]);
                            }
                        }
                    }
                }
            }

//            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
//                for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
//                    int plotID = plotCtr * nrRows + rowCtr + 1;
//                    indicator = GLPK.glp_mip_col_val(lp, plotID);
//                    System.out.println("Plot " + plotCtr +  " in Row " + rowCtr + ": " + plotID + ","
//                            + indicator + ", PlotID: " + plotID);
//                }
//            }
//
//            for (int queryCtr = 0; queryCtr < nrQueryInPlotsInRows; queryCtr++) {
//                int queryID = queryCtr + nrPlotInRows + 1;
//                indicator = GLPK.glp_mip_col_val(lp, queryID);
//                System.out.println("Highlighted Query: " + queryID + "," + indicator);
//            }
//            System.out.println("Uncolored Query!");
//            for (int queryCtr = 0; queryCtr < nrQueryInPlotsInRows; queryCtr++) {
//                int queryID = queryCtr + nrQueryInPlotsInRows + nrPlotInRows + 1;
//                indicator = GLPK.glp_mip_col_val(lp, queryID);
//                System.out.println("Uncolored Query: " + queryID + "," + indicator);
//            }
//
//
//            System.out.println("Product:");
//            for (int productCtr = 0; productCtr < nrProducts; productCtr++) {
//                int productID = productCtr + nrQueryInPlotsInRows * 2 + nrPlotInRows + 1;
//                indicator = GLPK.glp_mip_col_val(lp, productID);
//                System.out.println("Product: " + productID + "," + indicator);
//            }
            long endMillis = System.currentTimeMillis();
            PlanStats.initMillis = buildMillis - startMillis;
            PlanStats.buildMillis = optimizeMillis - buildMillis;
            PlanStats.optimizeMillis = endMillis - optimizeMillis;
            PlanStats.isTimeout = false;
            double value = GLPK.glp_mip_obj_val(lp);
            PlanStats.waitTime = value + readTime;
            System.out.println("Wait Time: " + (value + readTime) + " ms");
        }
        else {
            long endMillis = System.currentTimeMillis();
            PlanStats.initMillis = buildMillis - startMillis;
            PlanStats.buildMillis = optimizeMillis - buildMillis;
            PlanStats.optimizeMillis = endMillis - optimizeMillis;
            PlanStats.isTimeout = true;
            double value = GLPK.glp_mip_obj_val(lp);
            PlanStats.waitTime = value + readTime;
            System.out.println("Wait Time: " + (value + readTime) + " ms");
        }
        int rowCtr = 1;
        for (Map<String, List<DataPoint>> resultsPerRow: results) {
            System.out.println("Row: " + rowCtr);
            for (String groupVal: resultsPerRow.keySet()) {
                System.out.println("Group by: " + groupVal);
                for (DataPoint dataPoint: resultsPerRow.get(groupVal)) {
                    System.out.println(factory.queryString(dataPoint) +
                            "\tScore:" + dataPoint.probability + "\tRed:" + dataPoint.highlighted);
                }
            }
            rowCtr++;
        }
        // Free memory
        GLPK.glp_delete_prob(lp);
        return results;
    }

    public static String idToString(int varID, int[][] queriesToPlots, int[][] plotsToQueries,
                                    int nrRows, int allQueries, int nrProducts) {
        int nrPlots = plotsToQueries.length;
        int nrQueries = queriesToPlots.length;

        if (varID < nrPlots * nrRows + 1) {
            return "Plot " + ((varID - 1) / nrRows) + " in Row " + ((varID - 1) % nrRows);
        }
        else if (varID < nrPlots * nrRows + 1 + allQueries) {
            int queryIndex = varID - 1 - nrPlots * nrRows;
            int sum = 0;
            int targetCtr = -1;
            int targetPlot = -1;
            int targetRow = -1;
            for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
                int nrPlotsForQuery = queriesToPlots[queryCtr][0];
                if (sum + nrPlotsForQuery * nrRows > queryIndex) {
                    targetCtr = queryCtr;
                    targetPlot = queriesToPlots[queryCtr][(queryIndex - sum) / nrRows + 1];
                    targetRow = (queryIndex - sum) % nrRows;
                    break;
                }
                sum += nrPlotsForQuery * nrRows;
            }
            if (targetCtr == -1) {
                System.out.println("Wrong");
            }
            return "Highlighted Query " + targetCtr + " in Plot " + targetPlot + " in Row " + targetRow;
        }
        else if (varID < nrPlots * nrRows + 1 + allQueries * 2) {
            int queryIndex = varID - 1 - nrPlots * nrRows - allQueries;
            int sum = 0;
            int targetCtr = -1;
            int targetPlot = -1;
            int targetRow = -1;
            for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
                int nrPlotsForQuery = queriesToPlots[queryCtr][0];
                if (sum + nrPlotsForQuery * nrRows > queryIndex) {
                    targetCtr = queryCtr;
                    targetPlot = queriesToPlots[queryCtr][(queryIndex - sum) / nrRows + 1];
                    targetRow = (queryIndex - sum) % nrRows;
                    break;
                }
                sum += nrPlotsForQuery * nrRows;
            }
            return "Uncolored Query " + targetCtr + " in Plot " + targetPlot + " in Row " + targetRow;
        }
        else if (varID < nrPlots * nrRows + 1 + allQueries * 2 + nrProducts + nrPlots) {
            return "Highlighted Plot " + (varID - 1 - nrPlots * nrRows - allQueries * 2 - nrProducts);
        }

        else if (varID < nrPlots * nrRows + 1 + allQueries * 2 + nrProducts + nrPlots * 2) {
            return "Uncolored Plot " + (varID - 1 - nrPlots * nrRows - allQueries * 2 - nrProducts - nrPlots);
        }
        return "";
    }

    public static void main(String[] args) throws IOException, ParseException, JSQLParserException, SQLException {
        String query = "SELECT count(*) FROM sample_311 WHERE \"intersection_street_1\"='EAST  110 STREET'";
        PlanConfig.TOPK = 5;
        PlanConfig.R = 300;
        PlanConfig.PROCESSING_WEIGHT = 0;
        PlanConfig.NR_ROWS = 1;
        QueryFactory queryFactory = new QueryFactory(query);

        plan(queryFactory.queries, queryFactory.nrDistinctValues, 1, PlanConfig.R, queryFactory);

    }
}
