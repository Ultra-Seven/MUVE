package config;

/**
 * Configures integer linear programming based
 * plan for optimal interface generation.
 *
 * @author Ziyun Wei
 *
 */
public class PlanConfig {
    /**
     * The total width of interface area where plots are
     * aligned.
     */
    public static final int R = 900;
    /**
     * The width of one data point that represents a query.
     */
    public static final int B = 50;
    /**
     * The constant pixels of width in each plot.
     */
    public static final int C = 50;
    /**
     * The number of rows in given area.
     */
    public static final int NR_ROWS = 2;
    /**
     * Select first few queries that have the highest probability.
     */
    public static final int TOPK = 50;
    /**
     * The Number of ranks that each documents will be assigned.
     */
    public static final int NR_RANKS = 7;
    /**
     * The size of ranks is increasing exponentially. The base for
     * according number of ranks.
     */
    public static final double BASE = 1.16;
}
