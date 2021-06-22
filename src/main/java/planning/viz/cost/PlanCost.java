package planning.viz.cost;

import connector.PSQLConnector;
import planning.query.QueryFactory;
import planning.viz.DataPoint;
import planning.viz.Plot;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Estimate the processing cost for each plan
 * by looking up the explain output from Postgres
 * optimizer
 *
 * @author Ziyun Wei
 */
public class PlanCost {
    /**
     * We assume the cost model is a linear function.
     * Generate a list of explain SQL queries and extract
     * the intercept and slope of the cost model for each
     * plot and corresponding queries.
     *
     * @param plots             Set of plots in the optimization problem
     * @param queryFactory      Factory to generate similar queries
     */
    public static void processCost(Collection<Plot> plots, QueryFactory queryFactory) {
        List<String> explainQueries = new ArrayList<>(queryFactory.queries.length);
        String firstQuery = queryFactory.sqlWithoutPredicates();
        explainQueries.add(firstQuery + ";");
        // Generate explain queries to analyze unit cost of unary predicates
        for (Plot plot : plots) {
            String newQuery = firstQuery + " WHERE "
                    + queryFactory.combinedPredicate(plot, plot.nrDataPoints);
            explainQueries.add(newQuery + ";");
        }
//        long timer1 = System.currentTimeMillis();
        String costRegex = "[0-9]+\\.[0-9][0-9]\\.\\.[0-9]+\\.[0-9][0-9]";
        Pattern pattern = Pattern.compile(costRegex);
        try {
            // Extract cost information from the explain output
            List<Double> explainCost = PSQLConnector.getConnector().explain(explainQueries)
                    .stream().filter(output -> output.startsWith("  ->  Seq Scan "))
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

            // Cost of first query
            double initialCost = explainCost.remove(0);
            int outputCtr = 0;
            for (Plot plot : plots) {
                double delta = explainCost.get(outputCtr) - initialCost;
                double cost = delta / plot.nrDataPoints;
                for (DataPoint dataPoint: plot.dataPoints) {
                    dataPoint.setCost(cost);
                }
                plot.setCost(initialCost);
                outputCtr++;
            }
        } catch (SQLException exception) {
            for (Plot plot : plots) {
                for (DataPoint dataPoint: plot.dataPoints) {
                    dataPoint.setCost(0);
                }
                plot.setCost(0);
            }
        }

//        long timer2 = System.currentTimeMillis();
//        System.out.println("Explain time: " + (timer2 - timer1) + " ms");
    }
}
