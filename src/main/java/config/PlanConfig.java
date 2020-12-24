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
    public static final int TOPK = 100;

}
