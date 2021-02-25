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
    public static int R = 900;
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
    public static int NR_ROWS = 2;
    /**
     * Select first few queries that have the highest probability.
     */
    public static int TOPK = 10;
    /**
     * Duration of reading each title.
     */
    public static final int READ_TITLE = 2000;
    /**
     * Duration of reading a data point.
     */
    public static final int READ_DATA = 1000;
    /**
     * The significance of processing time in objective function.
     */
    public static final double PROCESSING_WEIGHT = 0.5;
    /**
     * The penalty time when the query is absent in the screen.
     */
    public static int PENALTY_TIME = 10000;
    /**
     * The time out of planner.
     */
    public static final int TIME_OUT = 5000;
}
