package planning.viz;

import config.PlanConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Each plot includes a set of related data points
 * which have the same values except only one freedom
 * index. The free index can be a column or value.
 *
 * @author Ziyun Wei
 */
public class Plot {
    /**
     * Identification of the plot.
     */
    public final int plotID;
    /**
     * All data points that can be included in the plot.
     */
    public final List<DataPoint> dataPoints;
    /**
     * Index where queries are distinguished by the different value.
     */
    public final int freeIndex;
    /**
     * Number of potential data points in the plot
     */
    public int nrDataPoints = 0;
    /**
     * Constant intercept of cost when queries are decided to be executed
     * together in the form of the plot. Generally, the cost of plot
     * increase linearly by the number of queries.
     * For the column with different values, queries are combined by
     * IN predicate. Otherwise, predicates of queries are connected
     * by OR.
     */
    public double cost = 0;
    /**
     * The probability of plot that is calculated by accumulating
     * probabilities over all queries in the plot.
     */
    public double probability = 0;
    /**
     * Number of highlighted queries.
     */
    public int nrHighlighted = 0;

    /**
     * Initialize an instance of plot
     *
     * @param plotID        plot's id
     * @param freeIndex     free index of vector
     */
    public Plot(int plotID, int freeIndex) {
        this.plotID = plotID;
        this.freeIndex = freeIndex;
        this.dataPoints = new ArrayList<>(PlanConfig.TOPK);
    }

    /**
     * Add a related data point into the plot.
     *
     * @param dataPoint     data point that represents a query
     */
    public void addDataPoint(DataPoint dataPoint) {
        this.dataPoints.add(dataPoint);
        nrDataPoints++;
        probability += dataPoint.probability;
    }

    /**
     * Sort the data points by decreasing order of probability.
     */
    public void sortByProbability() {
        dataPoints.sort((dataPoint1, dataPoint2) ->
                Double.compare(dataPoint2.probability, dataPoint1.probability));
    }
    /**
     * Set the constant intercept of cost
     * when queries of plot are executed.
     *
     * @param cost          constant cost of the plot
     */
    public void setCost(double cost) {
        this.cost = cost;
    }

    /**
     * Remove the data point from the plot.
     *
     * @param dataPoint     data point to be removed
     */
    public void removeDataPoint(DataPoint dataPoint) {
        int decrease = dataPoints.remove(dataPoint) ? 1 : 0;
        nrDataPoints -= decrease;
        probability -= decrease * dataPoint.probability;
    }

    /**
     * Whether two plots are equivalent.
     *
     * @param plot      another plot
     */
    public boolean equals(Plot plot) {
        if (nrDataPoints != plot.nrDataPoints) {
            return false;
        }
        for (int dataCtr = 0; dataCtr < nrDataPoints; dataCtr++) {
            if (dataPoints.get(dataCtr) != plot.dataPoints.get(dataCtr)) {
                return false;
            }
        }
        return true;
    }
}
