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
    public static int NR_ROWS = 1;
    /**
     * Select first few queries that have the highest probability.
     */
    public static int TOPK = 5;
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
    public static double PROCESSING_WEIGHT = 0.0;
    /**
     * The penalty time when the query is absent in the screen.
     */
    public static int PENALTY_TIME = 10000;
    /**
     * The time out of planner.
     */
    public static final int TIME_OUT = 5000;
    /**
     * The maximum cost of processing queries.
     */
    public static int MAX_PROCESSING_COST = 5000;
    /**
     * The cost of one query.
     */
    public static final int QUERY_COST = 25;
    /**
     * The cost of one plot.
     */
    public static final int PLOT_COST = 50;
    /**
     * The ratio of sampling data.
     */
    public static double SAMPLING_RATE = 1;
    /**
     * Threshold timeout for ILP algorithm.
     */
    public static double TIMEOUT = 1.0;
    /**
     * The threshold of interactive queries.
     */
    public static int THRESHOLD = 2000;
    /**
     * The parameters that maps query cost to processing time.
     */
    public static double PROCESSING_PARAMETER = 0.005;
    /**
     * The bias of linear regression.
     */
    public static double BIAS = 735.21122;
}
