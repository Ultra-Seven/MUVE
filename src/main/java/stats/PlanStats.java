package stats;

/**
 * The statistics of a plan when the optimal
 * multiplots is generated by greedy planner
 * or ILP planner
 */
public class PlanStats {
    /**
     * Number of queries input to the planner.
     */
    public static int nrQueries = 0;
    /**
     * Number of plots input to the planner.
     */
    public static int nrPlots = 0;
    /**
     * The expected wait time of optimal plan.
     */
    public static double waitTime = 0;
    /**
     * Duration of initializing the optimization problem.
     */
    public static long initMillis = 0;
    /**
     * Duration of building the optimization problem.
     */
    public static long buildMillis = 0;
    /**
     * Duration of optimizing the problem.
     */
    public static long optimizeMillis = 0;
    /**
     * Whether to generate optimal plan within timeout.
     */
    public static boolean isTimeout = false;
}
