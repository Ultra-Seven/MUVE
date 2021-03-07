package connector;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
/**
 * The instance of database connector that establishes
 * the connection with the Postgres database
 *
 * @author Ziyun Wei
 */
public class PSQLConnector extends DBConnector{
    public PSQLConnector() {
        String url = "jdbc:postgresql://localhost:5432/nycopen";
        Properties props = new Properties();
        props.setProperty("user", "postgres");
        props.setProperty("password", "postgres");
        try {
            connection = DriverManager.getConnection(url, props);
            System.out.println("Connected to postgres successfully");
        } catch (SQLException throwables) {
            System.out.println("Error in connecting to postgres.");
        }
    }

    /**
     * Use singleton pattern to get the instance of DBConnector.
     *
     * @return      The instance of DB connector.
     */
    public static DBConnector getConnector() {
        if (dbConnector == null) {
            dbConnector = new PSQLConnector();
        }
        return dbConnector;
    }


    public List<String> execute(String query) throws SQLException {
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        List<String> results = new ArrayList<>();
        while (rs.next()) {
            String targetName = rs.getString(1);
            results.add(targetName);
        }
        rs.close();
        return results;
    }

    public List<String> explain(List<String> queries) throws SQLException {
        Statement stmt = connection.createStatement();
        List<String> explainOutput = new ArrayList<>(queries.size());
        for (String query: queries) {
            ResultSet rs = stmt.executeQuery("EXPLAIN " + query);
            while (rs.next()) {
                explainOutput.add(rs.getString(1));
            }
        }
        return explainOutput;
    }
}
