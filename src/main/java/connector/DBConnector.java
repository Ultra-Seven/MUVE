package connector;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Abstract database connector. Subclass needs to
 * implement functions specific to database engine.
 *
 * @author Ziyun Wei
 */
public abstract class DBConnector {
    /**
     * Singleton instance of DB connector.
     */
    public static DBConnector dbConnector;
    /**
     * Connection object to the database engine.
     */
    protected Connection connection;

    /**
     * Given a list of queries, generate the explain
     * output from the database optimizer.
     *
     * @param queries           List of queries to analyze
     * @return                  Explain output for each query
     * @throws SQLException
     */
    public abstract List<String> explain(List<String> queries) throws SQLException;

    public abstract List<String> execute(String query) throws SQLException;
}
