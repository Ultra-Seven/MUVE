package planning.viz;
import config.PlanConfig;
import gurobi.*;
import net.sf.jsqlparser.JSQLParserException;
import org.apache.lucene.queryparser.classic.ParseException;
import planning.query.QueryFactory;
import planning.viz.cost.PlanCost;
import stats.PlanStats;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class WaitTimeGurobiPlanner {
    private static final boolean PRINT_LOGS = false;
    public static boolean Processing_Constraint = false;
    public static List<Map<String, List<DataPoint>>> plan(DataPoint[] scorePoints,
                                                          int[] maxIndices,
                                                          int nrRows, int R,
                                                          QueryFactory factory)
            throws IOException, SQLException, GRBException {
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
        if (PlanConfig.PROCESSING_WEIGHT > Double.MIN_VALUE) {
            PlanCost.processCost(idToPlots.values(), factory);
        }
        else {
            for (DataPoint dataPoint: scorePoints) {
                dataPoint.cost = 0;
            }
        }

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

        // Define columns
        int nrPlotInRows = nrRows * nrPlots;
        int nrQueryInPlotsInRows = Arrays.stream(queriesToPlots).reduce(0,
                (result, array)->result + array[0],Integer::sum) * nrRows;
        int nrProducts = nrQueries * 2;
        int nrPlotsForm = nrPlots * 2;

        // Create empty environment, set options, and start
        GRBEnv env = new GRBEnv(true);

//        env.set("logFile", "waitTime.log");
        env.start();

        // Create empty model
        GRBModel model = new GRBModel(env);

        GRBVar[] vars = new GRBVar[nrPlotInRows + nrQueryInPlotsInRows * 2 + nrProducts + nrPlotsForm];
        // Plot variables
        int startIndex = 0;
        for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                String plotName = "p_" + rowCtr + "_" + plotCtr;
                GRBVar plot = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, plotName);
                vars[startIndex] = plot;
                startIndex++;
            }
        }
        // Highlighted query variables
        for (int queryCtr = 0; queryCtr < nrQueryInPlotsInRows; queryCtr++) {
            String queryName = "h_" + queryCtr;
            GRBVar highlightQuery = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, queryName);
            vars[startIndex] = highlightQuery;
            startIndex++;
        }
        // Uncolored query variables
        for (int queryCtr = 0; queryCtr < nrQueryInPlotsInRows; queryCtr++) {
            String queryName = "u_" + queryCtr;
            GRBVar uncoloredQuery = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, queryName);
            vars[startIndex] = uncoloredQuery;
            startIndex++;
        }
        // Product variables
        int nrQueryInPlots = nrQueryInPlotsInRows / nrRows;
        int maxTime = PlanConfig.READ_DATA * nrQueries + PlanConfig.READ_TITLE * nrPlots;
        for (int formCtr = 0; formCtr < 2; formCtr++) {
            for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
                String productName = "product_" + formCtr + "_" + queryCtr;
                GRBVar product = model.addVar(0.0, maxTime, 0.0, GRB.INTEGER, productName);
                vars[startIndex] = product;
                startIndex++;
            }
        }
        // Plot form
        for (int formCtr = 0; formCtr < 2; formCtr++) {
            for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
                String plotName = "plot_" + formCtr + "_" + plotCtr;
                GRBVar plot = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, plotName);
                vars[startIndex] = plot;
                startIndex++;
            }
        }

        int constraintID = 0;
        // Constraint 1: Query belongs to at most one row.
        GRBLinExpr expr;
        for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
            expr = new GRBLinExpr();
            String constraint = "c_" + constraintID;
            int[] plotsForQuery = queriesToPlots[queryCtr];
            int nrPlotsForQuery = plotsForQuery[0];
            int offset = queriesPlotOffsets[queryCtr];
            for (int formCtr = 0; formCtr < 2; formCtr++) {
                for (int queryPlotCtr = 1; queryPlotCtr <= nrPlotsForQuery; queryPlotCtr++) {
                    for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                        int varID = (queryPlotCtr + offset - 1) * nrRows +
                                rowCtr + formCtr * nrQueryInPlotsInRows + nrPlotInRows;
                        expr.addTerm(1, vars[varID]);
                    }
                }
            }
            model.addConstr(expr, GRB.LESS_EQUAL, 1, constraint);
            constraintID++;
        }

        // Constraint 2: Query will be selected if and only if
        // one of the associated plots is generated.
        for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
            for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
                String constraint = "c_" + constraintID;
                expr = new GRBLinExpr();
                int nrQueriesInPlot = plotsToQueries[plotCtr][0];
                // Sum of exist indicator for plots
                int wID = plotCtr * nrRows + rowCtr;
                expr.addTerm(1, vars[wID]);
                // Query in a row
                int[] variableIDs = new int[nrQueriesInPlot * nrRows * 2];
                int index = 0;
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
                    for (int formCtr = 0; formCtr < 2; formCtr++) {
                        int queryID = formCtr * nrQueryInPlotsInRows + (queriesPlotOffsets[queryCtr] +
                                plotCtrInQueryIndex) * nrRows + rowCtr + nrPlotInRows;
                        variableIDs[index] = queryID;
                        expr.addTerm(-1, vars[queryID]);
                        index++;
                    }
                }
                model.addConstr(expr, GRB.LESS_EQUAL, 0, constraint);
                constraintID++;

                // highlighted/uncolored plot indicator lower bound
                constraint = "c_" + constraintID;
                expr = new GRBLinExpr();
                // Sum of exist indicator for plots
                expr.addTerm(nrQueriesInPlot, vars[wID]);

                for (int queryID : variableIDs) {
                    expr.addTerm(-1, vars[queryID]);
                }
                model.addConstr(expr, GRB.GREATER_EQUAL, 0, constraint);
                constraintID++;



            }
        }
//        for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
//            int queryNrPlots = queriesToPlots[queryCtr][0];
//            int offset = queriesPlotOffsets[queryCtr];
//            for (int plotCtr = 1; plotCtr <= queryNrPlots; plotCtr++) {
//                String constraint = "c_" + constraintID;
//                expr = new GRBLinExpr();
//                // Sum of exist indicator for plots
//                for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
//                    int plotID = queriesToPlots[queryCtr][plotCtr];
//                    int wID = plotID * nrRows + rowCtr;
//                    System.out.println(WaitTimePlanner.idToString(wID + 1, queriesToPlots, plotsToQueries,
//                            nrRows, nrQueryInPlotsInRows, nrProducts));
//                    expr.addTerm(1, vars[wID]);
//                }
//                // Query in a row
//                for (int formCtr = 0; formCtr < 2; formCtr++) {
//                    for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
//                        int varID = (plotCtr + offset - 1) * nrRows +
//                                rowCtr + formCtr * nrQueryInPlotsInRows + nrPlotInRows;
//                        expr.addTerm(-1, vars[varID]);
//                    }
//                }
//                model.addConstr(expr, GRB.GREATER_EQUAL, 0, constraint);
//                constraintID++;
//            }
//        }

        // Constraint 3: Plot can be shown in exactly one row.
        for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
            String constraint = "c_" + constraintID;
            expr = new GRBLinExpr();

            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                int plotID = plotCtr * nrRows + rowCtr;
                expr.addTerm(1, vars[plotID]);
            }
            model.addConstr(expr, GRB.LESS_EQUAL, 1, constraint);
            constraintID++;
        }

        // Constraint 4: Plots in one row cannot exceed the area width.
        for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
            String constraint = "c_" + constraintID;
            expr = new GRBLinExpr();
            // Constant pixels
            for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
                int plotID = plotCtr * nrRows + rowCtr;
                expr.addTerm(PlanConfig.C, vars[plotID]);
            }
            // Data points pixels
            for (int queryCtr = 0; queryCtr < nrQueryInPlots; queryCtr++) {
                int highlightedID = queryCtr * nrRows + rowCtr + nrPlotInRows;
                int uncoloredID = queryCtr * nrRows + rowCtr + nrPlotInRows + nrQueryInPlotsInRows;
                expr.addTerm(PlanConfig.B, vars[highlightedID]);
                expr.addTerm(PlanConfig.B, vars[uncoloredID]);
            }
            model.addConstr(expr, GRB.LESS_EQUAL, R, constraint);
            constraintID++;
        }

        // Constraint 5: At most half of queries are highlighted.
        expr = new GRBLinExpr();
        for (int queryCtr = 0; queryCtr < nrQueryInPlots; queryCtr++) {
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                int queryID = queryCtr * nrRows + rowCtr + nrPlotInRows;
                expr.addTerm(1, vars[queryID]);
            }
        }
        for (int queryCtr = 0; queryCtr < nrQueryInPlots; queryCtr++) {
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                int queryID = queryCtr * nrRows + rowCtr + nrPlotInRows + nrQueryInPlotsInRows;
                expr.addTerm(-1, vars[queryID]);
            }
        }
        model.addConstr(expr, GRB.LESS_EQUAL, 0, "c_" + constraintID);
        constraintID++;

        // Constraint 6: Specify whether plot contains highlighted and uncolored queries.
        for (int formCtr = 0; formCtr < 2; formCtr++) {
            for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
                // highlighted/uncolored plot indicator upperbound
                String constraint = "c_" + constraintID;
                expr = new GRBLinExpr();

                int hPlotID = formCtr * nrPlots + plotCtr + nrProducts + nrQueryInPlotsInRows * 2 + nrPlotInRows;
                expr.addTerm(1, vars[hPlotID]);
                int nrQueriesInPlot = plotsToQueries[plotCtr][0];
                int[] variableIDs = new int[nrQueriesInPlot * nrRows];
                int index = 0;
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
                                plotCtrInQueryIndex) * nrRows + rowCtr + nrPlotInRows;
                        variableIDs[index] = queryID;
                        expr.addTerm(-1, vars[queryID]);
                        index++;
                    }
                }
                model.addConstr(expr, GRB.LESS_EQUAL, 0, constraint);
                constraintID++;

                // highlighted/uncolored plot indicator lower bound
                constraint = "c_" + constraintID;
                expr = new GRBLinExpr();
                expr.addTerm(nrQueriesInPlot, vars[hPlotID]);

                for (int queryID : variableIDs) {
                    expr.addTerm(-1, vars[queryID]);
                }
                model.addConstr(expr, GRB.GREATER_EQUAL, 0, constraint);
                constraintID++;
            }
        }

        // Constraint 7: Product of query variable and penalty
//        int readTime = PlanConfig.PENALTY_TIME;
        int readTime = maxTime;

        int[] formOffsets = new int[]{0, nrQueryInPlotsInRows};
        for (int formCtr = 0; formCtr < 2; formCtr++) {
            int formOffset = formOffsets[formCtr];
            for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
                // case 1 for highlighted queries: M >= DR + Mq - y
                String constraint = "c_" + constraintID;
                expr = new GRBLinExpr();
                int productID = formCtr * nrQueries + queryCtr + nrQueryInPlotsInRows * 2 + nrPlotInRows;

                int nrPlotsForQuery = queriesToPlots[queryCtr][0];
                int offset = queriesPlotOffsets[queryCtr];

                double expectedConstant = formCtr == 0 ? 0.5 : 1;
                int min = offset * nrRows + nrPlotInRows + formOffset;
                int max = offset * nrRows + nrPlotsForQuery * nrRows + nrPlotInRows + formOffset;
                for (int queryCombineCtr = 0; queryCombineCtr < nrQueryInPlotsInRows; queryCombineCtr++) {
                    int varID = queryCombineCtr + nrPlotInRows;
                    double constant = varID >= min && varID < max ?
                            expectedConstant * PlanConfig.READ_DATA + maxTime :
                            expectedConstant * PlanConfig.READ_DATA;
//                    System.out.println(WaitTimePlanner.idToString(varID + 1,
//                            queriesToPlots, plotsToQueries, nrRows, nrQueryInPlotsInRows, nrProducts) + " " + constant);
                    expr.addTerm(constant, vars[varID]);
                }

                if (formCtr == 1) {
                    for (int queryCombineCtr = 0; queryCombineCtr < nrQueryInPlotsInRows; queryCombineCtr++) {
                        int varID = queryCombineCtr + nrPlotInRows + nrQueryInPlotsInRows;
                        double constant = varID >= min && varID < max ?
                                0.5 * PlanConfig.READ_DATA + maxTime :
                                0.5 * PlanConfig.READ_DATA;
//                        System.out.println(WaitTimePlanner.idToString(varID + 1,
//                                queriesToPlots, plotsToQueries, nrRows, nrQueryInPlotsInRows, nrProducts) + " " + constant);
                        expr.addTerm(constant, vars[varID]);
                    }
                }

                for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
                    int varID = plotCtr + nrProducts + nrQueryInPlotsInRows * 2 + nrPlotInRows;
//                    System.out.println(WaitTimePlanner.idToString(varID + 1,
//                            queriesToPlots, plotsToQueries, nrRows, nrQueryInPlotsInRows, nrProducts)
//                            + " " + (expectedConstant * PlanConfig.READ_TITLE));
                    expr.addTerm(expectedConstant * PlanConfig.READ_TITLE, vars[varID]);
                }

                if (formCtr == 1) {
                    for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
                        int varID = plotCtr + nrProducts + nrQueryInPlotsInRows * 2 + nrPlotInRows + nrPlots;
//                        System.out.println(WaitTimePlanner.idToString(varID + 1,
//                                queriesToPlots, plotsToQueries, nrRows, nrQueryInPlotsInRows, nrProducts)
//                                + " " + 0.5 * PlanConfig.READ_TITLE);
                        expr.addTerm(0.5 * PlanConfig.READ_TITLE, vars[varID]);
                    }
                }

                expr.addTerm(-1, vars[productID]);
                model.addConstr(expr, GRB.LESS_EQUAL, maxTime, constraint);
                constraintID++;

                // case 2 for plots: y - DR + Mq <= M
                constraint = "c_" + constraintID;
                expr = new GRBLinExpr();
                expr.addTerm(1, vars[productID]);
                for (int queryCombineCtr = 0; queryCombineCtr < nrQueryInPlotsInRows; queryCombineCtr++) {
                    int varID = queryCombineCtr + nrPlotInRows;
                    double constant = varID >= min && varID < max ?
                            -1 * expectedConstant * PlanConfig.READ_DATA + maxTime :
                            -1 * expectedConstant * PlanConfig.READ_DATA;
                    expr.addTerm(constant, vars[varID]);
                }

                if (formCtr == 1) {
                    for (int queryCombineCtr = 0; queryCombineCtr < nrQueryInPlotsInRows; queryCombineCtr++) {
                        int varID = queryCombineCtr + nrPlotInRows + nrQueryInPlotsInRows;
                        double constant = varID >= min && varID < max ?
                                -0.5 * PlanConfig.READ_DATA + maxTime :
                                -0.5 * PlanConfig.READ_DATA;
                        expr.addTerm(constant, vars[varID]);
                    }
                }

                for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
                    int varID = plotCtr + nrProducts + nrQueryInPlotsInRows * 2 + nrPlotInRows;
                    expr.addTerm(-1 * expectedConstant * PlanConfig.READ_TITLE, vars[varID]);
                }

                if (formCtr == 1) {
                    for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
                        int varID = plotCtr + nrProducts + nrQueryInPlotsInRows * 2 + nrPlotInRows + nrPlots;
                        expr.addTerm(-0.5 * PlanConfig.READ_TITLE, vars[varID]);
                    }
                }

                model.addConstr(expr, GRB.LESS_EQUAL, maxTime, constraint);
                constraintID++;

                // case 3 for plots: y - Mq <= 0
                constraint = "c_" + constraintID;
                expr = new GRBLinExpr();
                expr.addTerm(1, vars[productID]);
                for (int queryPlotCtr = 1; queryPlotCtr <= nrPlotsForQuery; queryPlotCtr++) {
                    for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                        int varID = (queryPlotCtr + offset - 1) * nrRows +
                                rowCtr + nrPlotInRows + formOffset;
                        expr.addTerm(-1 * maxTime, vars[varID]);
                    }
                }
                model.addConstr(expr, GRB.LESS_EQUAL, 0, constraint);
                constraintID++;
            }
        }

        // Optional Constraint 8: Limit cost of queries
        if (Processing_Constraint) {
            String constraint = "c_" + constraintID;
            expr = new GRBLinExpr();
            for (int plotCtr = 0; plotCtr < nrQueries; plotCtr++) {
                for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                    int varID = plotCtr * nrRows + rowCtr;
                    expr.addTerm(PlanConfig.PLOT_COST, vars[varID]);
                }
            }
            for (int queryCtr = 0; queryCtr < nrQueryInPlotsInRows * 2; queryCtr++) {
                int varID = queryCtr + nrPlotInRows;
                expr.addTerm(PlanConfig.QUERY_COST, vars[varID]);
            }
            model.addConstr(expr, GRB.LESS_EQUAL, PlanConfig.MAX_PROCESSING_COST, constraint);
            constraintID++;
        }

        // Set objective:
        expr = new GRBLinExpr();
        for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
            double probability = scorePoints[queryCtr].probability;
            double cost = scorePoints[queryCtr].cost;

            int nrPlotsForQuery = queriesToPlots[queryCtr][0];
            for (int plotCtr = 1; plotCtr <= nrPlotsForQuery; plotCtr++) {
                int variableIndex = queriesPlotOffsets[queryCtr] + plotCtr - 1;
                for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                    int highlightID = variableIndex * nrRows + rowCtr + nrPlotInRows;
                    int uncoloredID = variableIndex * nrRows + rowCtr + nrQueryInPlotsInRows + nrPlotInRows;

//                    double coefficient = PlanConfig.PROCESSING_WEIGHT > Double.MIN_VALUE ?
//                            (-1 * readTime + PlanConfig.PROCESSING_WEIGHT * cost) * probability:
//                            -1 * readTime * probability;
                    double coefficient = -1 * readTime * probability;
                    expr.addTerm(coefficient, vars[highlightID]);
                    expr.addTerm(coefficient, vars[uncoloredID]);
                }
            }
        }

        for (int formCtr = 0; formCtr < 2; formCtr++) {
            for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
                double probability = scorePoints[queryCtr].probability;
                int productID = formCtr * nrQueries + queryCtr +
                        + nrQueryInPlotsInRows * 2 + nrPlotInRows;
                expr.addTerm(probability, vars[productID]);
            }
        }
        long optimizeMillis = System.currentTimeMillis();
        model.setObjective(expr, GRB.MINIMIZE);
        model.set(GRB.DoubleParam.TimeLimit, 1.0);
        // Optimize model
        model.optimize();

        for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
            Map<String, List<DataPoint>> resultsPerRow = new HashMap<>();
            for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
                int plotID = plotCtr * nrRows + rowCtr;
                String name = vars[plotID].get(GRB.StringAttr.VarName);
                double value = vars[plotID].get(GRB.DoubleAttr.X);
                if (value > Double.MIN_VALUE) {
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
                    int queryID = variableIndex * nrRows + rowCtr + nrPlotInRows;
                    String name = vars[queryID].get(GRB.StringAttr.VarName);
                    double value = vars[queryID].get(GRB.DoubleAttr.X);

                    if (value > Double.MIN_VALUE) {
                        int plotIndex = queriesToPlots[queryCtr][plotCtr];
                        int plotID = plotIndex * nrRows + rowCtr;
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
                    int queryID = variableIndex * nrRows + rowCtr + nrQueryInPlotsInRows + nrPlotInRows;
                    String name = vars[queryID].get(GRB.StringAttr.VarName);
                    double value = vars[queryID].get(GRB.DoubleAttr.X);
                    if (value > Double.MIN_VALUE) {
                        int plotIndex = queriesToPlots[queryCtr][plotCtr];
                        int plotID = plotIndex * nrRows + rowCtr;
                        String contextName = String.valueOf(plotID);
                        if (resultsPerRow.containsKey(contextName)) {
                            resultsPerRow.get(contextName).add(scorePoints[queryCtr]);
                        }
                        else {
                            System.out.println("Wrong here!");
                        }
                    }
                }
            }
        }

        if (PRINT_LOGS) {
            for (int rowCtr = 0; rowCtr < nrRows; rowCtr++) {
                for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
                    int plotID = plotCtr * nrRows + rowCtr;
                    double value = vars[plotID].get(GRB.DoubleAttr.X);
                    System.out.println("Plot " + plotCtr +  " in Row " + rowCtr + ": " + plotID + ","
                            + value + ", PlotID: " + plotID);
                }
            }

            for (int queryCtr = 0; queryCtr < nrQueryInPlotsInRows; queryCtr++) {
                int queryID = queryCtr + nrPlotInRows;
                double value = vars[queryID].get(GRB.DoubleAttr.X);
                System.out.println("Highlighted Query: " + queryID + "," + value);
            }
            System.out.println("Uncolored Query!");
            for (int queryCtr = 0; queryCtr < nrQueryInPlotsInRows; queryCtr++) {
                int queryID = queryCtr + nrQueryInPlotsInRows + nrPlotInRows;
                double value = vars[queryID].get(GRB.DoubleAttr.X);
                System.out.println("Uncolored Query: " +
                        WaitTimePlanner.idToString(queryID + 1, queriesToPlots, plotsToQueries,
                                nrRows, nrQueryInPlotsInRows, nrProducts) +
                        "," + value);
            }


            System.out.println("Product:");
            for (int productCtr = 0; productCtr < nrProducts; productCtr++) {
                int productID = productCtr + nrQueryInPlotsInRows * 2 + nrPlotInRows;
                double value = vars[productID].get(GRB.DoubleAttr.X);
                System.out.println("Product: " + productID + "," + value);
            }


            for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
                int plotID = plotCtr + nrProducts + nrQueryInPlotsInRows * 2 + nrPlotInRows;
                double value = vars[plotID].get(GRB.DoubleAttr.X);
                System.out.println("Highlight Plot: " + plotID + "," + value);
            }

            for (int plotCtr = 0; plotCtr < nrPlots; plotCtr++) {
                int plotID = plotCtr + nrPlots + nrProducts + nrQueryInPlotsInRows * 2 + nrPlotInRows;
                double value = vars[plotID].get(GRB.DoubleAttr.X);
                System.out.println("Uncolored Plot: " + plotID + "," + value);
            }
        }

        int status = model.get(GRB.IntAttr.Status);

        if (status == GRB.OPTIMAL) {
            PlanStats.isTimeout = false;
        }
        else if (status == GRB.TIME_LIMIT) {
            System.out.println("Optimization was stopped with status " + status);
            PlanStats.isTimeout = true;
        }

        long endMillis = System.currentTimeMillis();
        PlanStats.initMillis = buildMillis - startMillis;
        PlanStats.buildMillis = optimizeMillis - buildMillis;
        PlanStats.optimizeMillis = endMillis - optimizeMillis;
        double value = model.get(GRB.DoubleAttr.ObjVal);
        PlanStats.waitTime = value + readTime;
        System.out.println("Wait Time: " + (value + readTime) + " ms");
        // Dispose of model and environment
        model.dispose();
        env.dispose();

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

        return results;
    }

    public static void main(String[] args) throws ParseException, JSQLParserException,
            IOException, GRBException, SQLException {
        String query = "SELECT count(*) FROM sample_311 WHERE \"intersection_street_1\"='EAST  110 STREET'";
        PlanConfig.TOPK = 5;
        PlanConfig.PROCESSING_WEIGHT = 0;
        PlanConfig.NR_ROWS = 1;
        PlanConfig.R = 300;
        QueryFactory queryFactory = new QueryFactory(query);
        plan(queryFactory.queries, queryFactory.nrDistinctValues, 1, PlanConfig.R, queryFactory);
    }
}
