package planning.viz;

import java.util.Arrays;

/**
 * Each data point represents a unique query
 * that is encoded as a vector. Data points
 * in a plot should have the same values
 * except only one freedom index.
 *
 * @author Ziyun Wei
 */
public class DataPoint {
    /**
     * Unique vector mapped from a SQL query.
     */
    public final int[] vector;
    /**
     * Confidence that the data point can answer user's request.
     */
    public double probability;
    /**
     * Cost of executing the corresponding query in the backend.
     */
    public double cost = Integer.MAX_VALUE;
    /**
     * Unique ID of each query.
     */
    public final int id;
    /**
     * Whether to highlight this query?
     */
    public boolean highlighted = false;

    /**
     * Initialize the data point instance.
     *
     * @param vector            unique vector to represent a query
     * @param probability       confidence to be the expected query
     * @param id                unique query id
     */
    public DataPoint(int[] vector, double probability, int id) {
        this.vector = vector;
        this.probability = probability;
        this.id = id;
    }

    /**
     * Set the cost of the data point
     * if the given cost is cheaper.
     *
     * @param cost      cost of the query
     */
    public void setCost(double cost) {
        this.cost = Math.min(cost, this.cost);
    }


    public String toString() {
        return Arrays.toString(vector);
    }
}
