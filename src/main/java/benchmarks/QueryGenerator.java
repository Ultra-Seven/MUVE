package benchmarks;


import matching.indexing.Indexer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.*;

import static matching.FuzzyIndex.INDEX_DIR;
import static matching.FuzzyIndex.PHONETIC_DIR;

/**
 * Generate a set of queries randomly for
 * given dataset. The number of replaceable
 * terms can be varied in the query template.
 */
public class QueryGenerator {
    /**
     * The name of dataset.
     */
    public final static String DATASET = "dob_job";
    /**
     * Number of queries for each dataset.
     */
    public final static int NR_QUERIES = 100;
    /**
     * Number of predicates.
     */
    public final static int NR_PREDICATES = 5;

    /**
     * Construct a SQL query by specifying given
     * number of predicates.
     *
     * @param indexes           index mapping column names to values
     * @param columnNames       valid column names
     * @param nrPreds           number of predicates
     * @return                  constructed SQL query
     */
    public static String generateSQL(Map<String, List<String>> indexes,
                                     List<String> columnNames,
                                     int nrPreds) {
        String[] prefix = new String[]{"SELECT count", "SELECT avg", "SELECT min", "SELECT max"};
        // Construct prefix
        String select = prefix[0] + "(*)";
        String fromTable = "FROM " + DATASET;
        // Specify the select clause
        String where = "WHERE ";
        List<String> predicates = new ArrayList<>(nrPreds);
        Collections.shuffle(columnNames);
        for (int predCtr = 0; predCtr < nrPreds; predCtr++) {
            String columnName = columnNames.get(predCtr);
            Random generator = new Random();
            List<String> values = indexes.get(columnName);
            String randomValue = values.get(generator.nextInt(values.size()));
            predicates.add("\"" + columnName + "\"" + "='" + randomValue + "'");
        }
        where = where + String.join(" and ", predicates);
        return select + " " + fromTable + " " + where + ";";
    }

    /**
     * Check whether columns is the one that is chosen
     * in SQL query frequently.
     *
     * @param columnName        name of column
     * @return                  valid flag
     */
    public static boolean isValidColumn(String columnName) {
        boolean isValid = true;
        switch (DATASET) {
            case "sample_311": {
                String[] columns = new String[]{"agency", "agency_name",
                        "complain_type", "descriptor_type", "location_type", "street_name",
                        "cross_street_1", "cross_street_2", "intersection_street_1",
                        "intersection_street_2", "address_type", "city", "landmark", "status",
                        "community_board", "borough", "channel_type", "park_borough"};
                isValid = Arrays.asList(columns).contains(columnName);
                break;
            }
            case "sample_au": {
                String[] columns = new String[]{"organisation_name", "first_name",
                        "middle_name", "last_name", "authority_level", "department",
                        "city", "state"};
                isValid = Arrays.asList(columns).contains(columnName);
                break;
            }
            case "dob_job": {
                String[] columns = new String[]{"borough", "job_status_descrption",
                        "action_month", "applicant_first_name", "applicant_last_name", "filing_month",
                        "paid_month", "fully_paid_month", "assigned_month",
                        "approved_month", "fee_status", "owner_type", "owner_first_name", "owner_last_name",
                        "city"};
                isValid = Arrays.asList(columns).contains(columnName);
                break;
            }
        }

        return isValid;
    }

    /**
     * Generate a set of queries for given benchmark.
     *
     * @throws IOException
     */
    public static void generateQueries() throws IOException {
        String searchDir = (Indexer.Phonetic ? PHONETIC_DIR : INDEX_DIR) + "/" + DATASET;
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(searchDir)));
        IndexSearcher searcher = new IndexSearcher(reader);
        Query query = new MatchAllDocsQuery();
        TopDocs topDocs = searcher.search(query, Integer.MAX_VALUE);
        ScoreDoc[] scoreDocs = topDocs.scoreDocs;
        Map<String, List<String>> columnToDocuments = new HashMap<>();

        List<String> queries = new ArrayList<>(NR_QUERIES);
        List<String> columnNames = new ArrayList<>();
        // Store documents into main memory
        for (int docCtr = 0; docCtr < scoreDocs.length; docCtr++) {
            ScoreDoc scoreDoc = scoreDocs[docCtr];
            Document doc = reader.document(scoreDoc.doc);
            String columnName = doc.get("column");
            String textValue = doc.get("text");
            if (isValidColumn(columnName) && !textValue.trim().isEmpty()) {
                if (!columnToDocuments.containsKey(columnName)) {
                    columnToDocuments.put(columnName, new ArrayList<>());
                    columnNames.add(columnName);
                }
                columnToDocuments.get(columnName).add(textValue);
            }
        }

        int nrQueriesForTemplate = NR_QUERIES / NR_PREDICATES;

        for (int predicateCtr = 1; predicateCtr <= NR_PREDICATES; predicateCtr++) {
            for (int queryCtr = 0; queryCtr < nrQueriesForTemplate; queryCtr++) {
                String sqlQuery = generateSQL(columnToDocuments, columnNames, predicateCtr);
                queries.add(sqlQuery);
            }
        }

        // Save queries into the file
        FileWriter fileWriter = new FileWriter("./queries/" + DATASET + "/queries.sql");
        PrintWriter printWriter = new PrintWriter(fileWriter);
        for (String sqlQuery: queries) {
            printWriter.println(sqlQuery);
        }
        printWriter.close();
    }

    public static void main(String[] args) throws IOException {
        generateQueries();
    }
}
