package planning.viz;

import config.PlanConfig;
import net.sf.jsqlparser.JSQLParserException;
import org.apache.lucene.queryparser.classic.ParseException;
import org.gnu.glpk.*;
import planning.query.QueryFactory;
import planning.viz.cost.PlanCost;

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

        // Initialize processing overhead from Postgres
        int nrPlots = idToPlots.size();
        PlanCost.processCost(idToPlots.values(), factory);

        // Sort the number of plot based on probability
        int[] firstPlots = new int[nrQueries];
        // Build a index mapping query to plots
        int maxPlot = 0;
        Map<Integer, Integer> plotIDToVarID = new HashMap<>(nrPlots);
        int[][] queriesToPlots = new int[nrQueries][nrDims+1];
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
                    queryNrPlots++;
                    offset++;
                }

                firstPlots[queryCtr] = maxPlot + 1;
            }
            queriesToPlots[queryCtr][0] = queryNrPlots;
        }

        // Create problem
        glp_prob lp = GLPK.glp_create_prob();
        System.out.println("Problem created");
        GLPK.glp_set_prob_name(lp, "Viz Wait Time ILP");
        // Define columns
        int nrQueryInRows = nrRows * nrQueries;
        int nrPlotInRows = nrRows * nrPlots;
        int nrProducts = nrQueries * 2;
        GLPK.glp_add_cols(lp, nrQueryInRows + nrPlotInRows + nrProducts);

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

        // Query variables
        for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                int varID = queryCtr * nrRows + rowCtr + startIndex;
                GLPK.glp_set_col_name(lp, varID, "d_" + rowCtr + "_" + queryCtr);
                GLPK.glp_set_col_kind(lp, varID, GLPKConstants.GLP_BV);
            }
        }

        startIndex += nrQueryInRows;

        // Product variables
        for (int queryCtr = 0; queryCtr < nrProducts; queryCtr++) {
            int varID = queryCtr + startIndex;
            GLPK.glp_set_col_name(lp, varID, "product_" + queryCtr);
            GLPK.glp_set_col_kind(lp, varID, GLPKConstants.GLP_IV);
            GLPK.glp_set_col_bnds(lp, varID, GLPKConstants.GLP_LO, 0., 0.);
        }

        startIndex += nrQueries;


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
        int nrConstraints = 1 + nrQueries + nrQueries * nrRows
                + nrPlots + nrRows + nrRows + nrProducts * 3;
        GLPK.glp_add_rows(lp, nrConstraints);
        startIndex = 1;
        // Context constraints
        SWIGTYPE_p_int rowInd = GLPK.new_intArray(nrRows + 1);
        SWIGTYPE_p_double rowVal = GLPK.new_doubleArray(nrRows + 1);

        // Constraint 1: Query belongs to at most one row.
        for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
            int constraintID = startIndex + queryCtr;
            GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
            GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_UP, 0., 1.);
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                int docID = queryCtr * nrRows + rowCtr + nrPlotInRows + 1;
                GLPK.intArray_setitem(rowInd, rowCtr + 1, docID);
                GLPK.doubleArray_setitem(rowVal, rowCtr + 1, 1.);
            }
            GLPK.glp_set_mat_row(lp, constraintID, nrRows, rowInd, rowVal);
        }
        startIndex += nrQueries;

        // Constraint 2: Query will be selected if and only if
        // one of the associated plots is generated.
        SWIGTYPE_p_int[] varInd = new SWIGTYPE_p_int[nrDims];
        SWIGTYPE_p_double[] varVal = new SWIGTYPE_p_double[nrDims];
        for (int dimCtr = 0; dimCtr < nrDims; dimCtr++) {
            varInd[dimCtr] = GLPK.new_intArray(dimCtr + 3);
            varVal[dimCtr] = GLPK.new_doubleArray(dimCtr + 3);
        }
        for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
            int queryNrPlots = queriesToPlots[queryCtr][0];
            SWIGTYPE_p_int targetInd = varInd[queryNrPlots - 1];
            SWIGTYPE_p_double targetVal = varVal[queryNrPlots - 1];
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                int constraintID = queryCtr * nrRows + rowCtr + startIndex;
                GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
                GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_LO, 0., 2.);
                // Sum of exist indicator for plots in a row
                for (int plotCtr = 1; plotCtr <= queryNrPlots; plotCtr++) {
                    int plotID = queriesToPlots[queryCtr][plotCtr];
                    int fID = plotID * nrRows + rowCtr + 1;
                    GLPK.intArray_setitem(targetInd, plotCtr, fID);
                    GLPK.doubleArray_setitem(targetVal, plotCtr, 1.);
                }
                // Query in a row
                int queryID = queryCtr * nrRows + rowCtr + nrPlotInRows + 1;
                GLPK.intArray_setitem(targetInd, queryNrPlots + 1, queryID);
                GLPK.doubleArray_setitem(targetVal, queryNrPlots + 1, -1.);

                GLPK.glp_set_mat_row(lp, constraintID, queryNrPlots + 1, targetInd, targetVal);
            }
        }
        startIndex += nrQueries * nrRows;

        // Constraint 3: Plot can be shown in exactly one row.
        for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
            int constraintID = plotCtr + startIndex;
            GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
            GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_UP, 0., 1.);

            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                int plotID = plotCtr * nrRows + rowCtr + 1;
                GLPK.intArray_setitem(rowInd, rowCtr + 1, plotID);
                GLPK.doubleArray_setitem(rowVal, rowCtr + 1, 1.);
            }
            GLPK.glp_set_mat_row(lp, constraintID, nrRows, rowInd, rowVal);

        }
        startIndex += nrPlots;

        // Constraint 4: Plots in one row cannot exceed the area width.
        SWIGTYPE_p_int widthInd = GLPK.new_intArray(nrQueries + nrPlots + 1);
        SWIGTYPE_p_double widthVal = GLPK.new_doubleArray(nrQueries + nrPlots + 1);
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
            for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
                int queryID = queryCtr * nrRows + rowCtr + nrPlotInRows + 1;
                GLPK.intArray_setitem(widthInd, queryCtr + 1 + nrPlots, queryID);
                GLPK.doubleArray_setitem(widthVal, queryCtr + 1 + nrPlots, PlanConfig.B);
            }
            GLPK.glp_set_mat_row(lp, constraintID, nrQueries + nrPlots, widthInd, widthVal);
        }

        startIndex += nrRows;

        //Constraint 5: at least one query for a row
        for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
            int constraintID = rowCtr + startIndex;
            GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
            GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_LO, 1., nrRows);
            for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
                int queryID = queryCtr * nrRows + rowCtr + nrPlotInRows + 1;
                GLPK.intArray_setitem(widthInd, queryCtr + 1, queryID);
                GLPK.doubleArray_setitem(widthVal, queryCtr + 1, 1);
            }
            GLPK.glp_set_mat_row(lp, constraintID, nrQueries, widthInd, widthVal);
        }
        startIndex += nrRows;

        // Constraint 6: Product of query variable and penalty
        int readTime = PlanConfig.READ_DATA * nrQueries + PlanConfig.READ_TITLE * nrPlots;
        int largeLen = Math.max(nrQueries - 1, nrPlots - 1) * nrRows + nrRows + 3;
        SWIGTYPE_p_int largeInd = GLPK.new_intArray(largeLen);
        SWIGTYPE_p_double largeVal = GLPK.new_doubleArray(largeLen);
        for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
            // case 1 for plots: ux1 - y >= 0
            int constraintID = queryCtr * 6 + startIndex;
            int titleUpperBound = firstPlots[queryCtr];
            int queryUpperBound = queryCtr;
            int nextPlot = firstPlots[queryCtr];
            int plotProductID = queryCtr * 2 + nrQueryInRows + nrPlotInRows + 1;
            int queryProductID = queryCtr * 2 + 1 + nrQueryInRows + nrPlotInRows + 1;
            GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
            GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_LO, 0., titleUpperBound);
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                int queryID = queryCtr * nrRows + rowCtr + nrPlotInRows + 1;
                GLPK.intArray_setitem(largeInd, rowCtr + 1, queryID);
                GLPK.doubleArray_setitem(largeVal, rowCtr + 1, titleUpperBound);
            }
            GLPK.intArray_setitem(largeInd, nrRows + 1, plotProductID);
            GLPK.doubleArray_setitem(largeVal, nrRows + 1, -1);
            GLPK.glp_set_mat_row(lp, constraintID, nrRows + 1, largeInd, largeVal);

            // case 1 for queries: ux1 - y >= 0
            constraintID++;
            GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
            GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_LO, 0., queryUpperBound);
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                int queryID = queryCtr * nrRows + rowCtr + nrPlotInRows + 1;
                GLPK.intArray_setitem(largeInd, rowCtr + 1, queryID);
                GLPK.doubleArray_setitem(largeVal, rowCtr + 1, queryUpperBound);
            }
            GLPK.intArray_setitem(largeInd, nrRows + 1, queryProductID);
            GLPK.doubleArray_setitem(largeVal, nrRows + 1, -1);
            GLPK.glp_set_mat_row(lp, constraintID, nrRows + 1, largeInd, largeVal);

            // case 2 for plots: sum(Ip) - y >= 0
            constraintID++;
            GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
            GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_LO, 0., titleUpperBound);
            for (int plotCtr = 0; plotCtr < nextPlot; plotCtr++) {
                for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                    int plotID = plotCtr * nrRows + rowCtr + 1;
                    GLPK.intArray_setitem(largeInd, plotID, plotID);
                    GLPK.doubleArray_setitem(largeVal, plotID, 1);
                }
            }
            int lastPlotPosition = nextPlot * nrRows;
            GLPK.intArray_setitem(largeInd, lastPlotPosition + 1, plotProductID);
            GLPK.doubleArray_setitem(largeVal, lastPlotPosition + 1, -1);
            GLPK.glp_set_mat_row(lp, constraintID, lastPlotPosition + 1, largeInd, largeVal);

            // case 2 for queries: sum(Iq) - y >= 0
            constraintID++;
            GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
            GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_LO, 0., titleUpperBound);
            for (int nextQueryCtr = 0; nextQueryCtr < queryCtr; nextQueryCtr++) {
                for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                    int queryID = nextQueryCtr * nrRows + rowCtr + nrPlotInRows + 1;
                    int position = nextQueryCtr * nrRows + rowCtr + 1;
                    GLPK.intArray_setitem(largeInd, position, queryID);
                    GLPK.doubleArray_setitem(largeVal, position, 1);
                }
            }
            int lastQueryPosition = queryCtr * nrRows;
            GLPK.intArray_setitem(largeInd, lastQueryPosition + 1, queryProductID);
            GLPK.doubleArray_setitem(largeVal, lastQueryPosition + 1, -1);
            GLPK.glp_set_mat_row(lp, constraintID, lastQueryPosition + 1, largeInd, largeVal);

            // case 3 for plots: sum(Ip) + u * x1 - y <= u
            constraintID++;
            GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
            GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_UP, 0., titleUpperBound);
            for (int plotCtr = 0; plotCtr < nextPlot; plotCtr++) {
                for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                    int plotID = plotCtr * nrRows + rowCtr + 1;
                    GLPK.intArray_setitem(largeInd, plotID, plotID);
                    GLPK.doubleArray_setitem(largeVal, plotID, 1);
                }
            }
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                int queryID = queryCtr * nrRows + rowCtr + nrPlotInRows + 1;
                GLPK.intArray_setitem(largeInd, lastPlotPosition + rowCtr + 1, queryID);
                GLPK.doubleArray_setitem(largeVal, lastPlotPosition + rowCtr + 1, titleUpperBound);
            }
            GLPK.intArray_setitem(largeInd, lastPlotPosition + nrRows + 1, plotProductID);
            GLPK.doubleArray_setitem(largeVal, lastPlotPosition + nrRows + 1, -1);
            GLPK.glp_set_mat_row(lp, constraintID, lastPlotPosition + nrRows + 1, largeInd, largeVal);

            // case 3 for queries: sum(Iq) + u * x1 - y <= u
            constraintID++;
            GLPK.glp_set_row_name(lp, constraintID, "c_" + constraintID);
            GLPK.glp_set_row_bnds(lp, constraintID, GLPKConstants.GLP_UP, 0., queryUpperBound);
            for (int nextQueryCtr = 0; nextQueryCtr < queryCtr; nextQueryCtr++) {
                for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                    int queryID = nextQueryCtr * nrRows + rowCtr + nrPlotInRows + 1;
                    int position = nextQueryCtr * nrRows + rowCtr + 1;
                    GLPK.intArray_setitem(largeInd, position, queryID);
                    GLPK.doubleArray_setitem(largeVal, position, 1);
                }
            }
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                int queryID = queryCtr * nrRows + rowCtr + nrPlotInRows + 1;
                GLPK.intArray_setitem(largeInd, lastQueryPosition + rowCtr + 1, queryID);
                GLPK.doubleArray_setitem(largeVal, lastQueryPosition + rowCtr + 1, queryUpperBound);
            }
            GLPK.intArray_setitem(largeInd, lastQueryPosition + nrRows + 1, queryProductID);
            GLPK.doubleArray_setitem(largeVal, lastQueryPosition + nrRows + 1, -1);
            GLPK.glp_set_mat_row(lp, constraintID, lastQueryPosition + nrRows + 1, largeInd, largeVal);
        }

        // Free memory
        GLPK.delete_intArray(rowInd);
        GLPK.delete_doubleArray(rowVal);
        GLPK.delete_intArray(largeInd);
        GLPK.delete_doubleArray(largeVal);


        for (int dimCtr = 0; dimCtr < nrDims; dimCtr++) {
            GLPK.delete_intArray(varInd[dimCtr]);
            GLPK.delete_doubleArray(varVal[dimCtr]);
        }

        GLPK.delete_intArray(widthInd);
        GLPK.delete_doubleArray(widthVal);

        // Define objective
        GLPK.glp_set_obj_name(lp, "z");
        GLPK.glp_set_obj_dir(lp, GLPKConstants.GLP_MIN);

        // Based on simple model: Read query one by one
        for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
            double probability = scorePoints[queryCtr].probability;
            double cost = scorePoints[queryCtr].cost;
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                int queryID = queryCtr * nrRows + rowCtr + nrPlotInRows + 1;
                double coefficient = -1 * readTime * probability + PlanConfig.Processing_WEIGHT * cost;
                GLPK.glp_set_obj_coef(lp, queryID, coefficient);
            }
            int plotProductID = queryCtr * 2 + nrPlotInRows + nrQueryInRows + 1;
            int queryProductID = queryCtr * 2 + nrPlotInRows + nrQueryInRows + 2;
            GLPK.glp_set_obj_coef(lp, plotProductID,
                    PlanConfig.READ_TITLE * probability);
            GLPK.glp_set_obj_coef(lp, queryProductID,
                    PlanConfig.READ_DATA * probability);
        }

        for (Plot plot: idToPlots.values()) {
            double cost = plot.cost;
            int plotCtr = plotIDToVarID.get(plot.plotID);
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                int plotID = plotCtr * nrRows + rowCtr + 1;
                GLPK.glp_set_obj_coef(lp, plotID, PlanConfig.Processing_WEIGHT * cost);
            }
        }

        // Solve the model
        glp_iocp iocp = new glp_iocp();
        GLPK.glp_init_iocp(iocp);
        glp_smcp simplexParams = new glp_smcp();
        GLPK.glp_init_smcp(simplexParams);
        GLPK.glp_simplex(lp, simplexParams);
        // Set time out
        iocp.setTm_lim(10000);
        simplexParams.setTm_lim(10000);
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

                for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
                    int queryID = queryCtr * nrRows + rowCtr + nrPlotInRows + 1;
                    name = GLPK.glp_get_col_name(lp, queryID);
                    indicator = GLPK.glp_mip_col_val(lp, queryID);

                    if (indicator > Double.MIN_VALUE) {
                        int queryNrPlots = queriesToPlots[queryCtr][0];
                        for (int plotCtr = 1; plotCtr <= queryNrPlots; plotCtr++) {
                            int plotIndex = queriesToPlots[queryCtr][plotCtr];
                            int plotID = plotIndex * nrRows + rowCtr + 1;
                            String contextName = String.valueOf(plotID);
                            if (resultsPerRow.containsKey(contextName)) {
                                resultsPerRow.get(contextName).add(scorePoints[queryCtr]);
                                break;
                            }
                        }
                    }
                }
                results.add(resultsPerRow);
            }


            double value = GLPK.glp_mip_obj_val(lp);
            System.out.println("Wait Time: " + (value + readTime) + " ms");
        }
        int rowCtr = 1;
        for (Map<String, List<DataPoint>> resultsPerRow: results) {
            System.out.println("Row: " + rowCtr);
            for (String groupVal: resultsPerRow.keySet()) {
                System.out.println("Group by: " + groupVal);
                for (DataPoint dataPoint: resultsPerRow.get(groupVal)) {
                    System.out.println(factory.queryString(dataPoint) + "\tScore:" + dataPoint.probability);
                }
            }
            rowCtr++;
        }
        // Free memory
        GLPK.glp_delete_prob(lp);
        return results;
    }
    public static void main(String[] args) throws IOException, ParseException, JSQLParserException, SQLException {
        String query = "SELECT count(*) FROM dob_job WHERE \"city\" = 'Bronx' and \"city\"=\"borough\";";
        QueryFactory queryFactory = new QueryFactory(query);

        plan(queryFactory.queries, queryFactory.nrDistinctValues, 2, PlanConfig.R, queryFactory);

    }
}
