package planning.query;

import config.PlanConfig;
import matching.FuzzySearch;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import planning.viz.DataPoint;
import planning.viz.Plot;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Query factory engine parses the SQL query text
 * and extract replaceable elements from the parsed
 * SQL template. Then it looks up Similarity Index
 * and replace the those elements by similar candidates.
 *
 * @author Ziyun Wei
 */
public class QueryFactory {
    /**
     * The querying dateset in the SQL.
     */
    public String dataset;
    /**
     * Parsed the SQL query into terms.
     * e.g. column names, functions, literals, special symbols
     */
    public final List<String> terms;
    /**
     * The index of term list where the term can be replaced.
     * Currently, we consider column name and literals.
     */
    public final List<Integer> replaceIndex;
    /**
     * The index of replaceable terms list
     * where the term is replaceable selected columns.
     */
    public final List<Integer> selectIndex;
    /**
     * The index of replaceable terms list
     * where the term is replaceable literal.
     */
    public final List<Integer> valueIndex;
    /**
     * The index of replaceable terms list
     * where the term is replaceable column.
     */
    public final List<Integer> columnIndex;
    /**
     * The index of replaceable terms list
     * where the term is replaceable joined column.
     */
    public final List<Integer> joinIndex;
    /**
     * Number of distinct candidates for each
     * replaceable terms.
     */
    public final int[] nrDistinctValues;
    /**
     * Map replaceable terms to unique integers.
     */
    public final Map<String, Integer>[] termToKey;
    /**
     * Map unique integers to replaceable terms.
     */
    public final String[][] keyToTerms;
    /**
     * Number of replaceable terms.
     */
    public final int nrDims;
    /**
     * A list of queries that are similar to parsed query.
     */
    public final DataPoint[] queries;
    /**
     * The position where the SELECT clause starts.
     */
    public int selectPos;
    /**
     * The position where the FROM clause starts.
     */
    public int fromPos;
    /**
     * The position where the WHERE clause starts.
     */
    public int wherePos;

    public QueryFactory (String query) throws IOException, ParseException, JSQLParserException {
        int maxLen = query.split(" ").length;
        this.terms = new ArrayList<>(maxLen);
        this.replaceIndex = new ArrayList<>(maxLen);
        this.selectIndex = new ArrayList<>(maxLen);
        this.joinIndex = new ArrayList<>(maxLen);
        this.valueIndex = new ArrayList<>(maxLen);
        this.columnIndex = new ArrayList<>(maxLen);
        parseQueryTemplate(query);
        this.nrDims = replaceIndex.size();
        this.termToKey = new HashMap[nrDims];
        this.keyToTerms = new String[nrDims][PlanConfig.TOPK];
        for (int setCtr = 0; setCtr < nrDims; setCtr++) {
            termToKey[setCtr] = new HashMap<>(PlanConfig.TOPK);
        }
        int nrValues = valueIndex.size();
        List<ScoreDoc[]> scoreDocs = new ArrayList<>(nrValues);
        int maxQueries = 1;
        for (int valuePos : valueIndex) {
            String value = terms.get(replaceIndex.get(valuePos));
            ScoreDoc[] docs = FuzzySearch.search(value, dataset);
            maxQueries *= docs.length;
            scoreDocs.add(docs);
        }

        // Transform documents to queries
        int nrQueries = Math.min(maxQueries, PlanConfig.TOPK);
        this.queries = new DataPoint[nrQueries];
        int[] pivots = new int[nrValues];
        this.nrDistinctValues = new int[nrDims];
        double sum = 0;

        // Match similar literals
        for (int queryCtr = 0; queryCtr < nrQueries; queryCtr++) {
            IndexSearcher searcher = FuzzySearch.searchers.get(dataset);
            int[] vector = new int[nrDims];
            double score = 0;
            double minScoreDiff = Integer.MAX_VALUE;
            int maxNextPivot = -1;
            for (int valueCtr = 0; valueCtr < nrValues; valueCtr++) {
                int docCtr = pivots[valueCtr];
                int valuePos = valueIndex.get(valueCtr);
                int columnPos = columnIndex.get(valueCtr);
                ScoreDoc[] valueScoreDocs = scoreDocs.get(valueCtr);
                ScoreDoc scoreDoc = valueScoreDocs[docCtr];
                Document document = searcher.doc(scoreDoc.doc);
                String column = document.get("column");
                String content = document.get("text");
                vector[columnPos] = termToKey[columnPos]
                        .computeIfAbsent(column, k -> (termToKey[columnPos].size() + 1));
                vector[valuePos] = termToKey[valuePos]
                        .computeIfAbsent(content, k -> (termToKey[valuePos].size() + 1));
                keyToTerms[columnPos][vector[columnPos]] = column;
                keyToTerms[valuePos][vector[valuePos]] = content;
                score += scoreDoc.score;
                // Next documents
                if (docCtr < valueScoreDocs.length - 1) {
                    ScoreDoc nextScoreDoc = valueScoreDocs[docCtr + 1];
                    double scoreDiff = scoreDoc.score - nextScoreDoc.score;
                    if (scoreDiff < minScoreDiff) {
                        minScoreDiff = scoreDiff;
                        maxNextPivot = valueCtr;
                    }
                }
            }

            // Join predicates
            for (Integer joinColumnPos: joinIndex) {
                String columnName = terms.get(replaceIndex.get(joinColumnPos));
                vector[joinColumnPos] = termToKey[joinColumnPos]
                        .computeIfAbsent(columnName, k -> (termToKey[joinColumnPos].size() + 1));
                keyToTerms[joinColumnPos][vector[joinColumnPos]] = columnName;
            }

            queries[queryCtr] = new DataPoint(vector, score);
            sum += score;
            // Find the next pivot
            if (maxNextPivot != -1) {
                pivots[maxNextPivot]++;
            }
        }
        // Normalize probability distribution
        for (DataPoint dataPoint: queries) {
            dataPoint.probability /= sum;
        }
        for (int dimCtr = 0; dimCtr < nrDims; dimCtr++) {
            nrDistinctValues[dimCtr] = termToKey[dimCtr].size() + 1;
        }
    }

    /**
     * Return the string to represent the given query.
     *
     * @param dataPoint     data point  of the query
     * @return              the string of the query
     */
    public String queryString(DataPoint dataPoint) {
        String[] map = new String[nrDims];
        for (int dimCtr = 0; dimCtr < nrDims; dimCtr++) {
            int index = dataPoint.vector[dimCtr];
            map[dimCtr] = keyToTerms[dimCtr][index];
        }
        return Arrays.toString(map);
    }

    /**
     * Return the SQL query without any predicates.
     *
     * @return      the text of SQL query
     */
    public String sqlWithoutPredicates() {
        List<String> selectTerms = terms.subList(0, fromPos);
        columnIndex.forEach(column -> {
            int termPos = replaceIndex.get(column);
            if (termPos < fromPos && termPos >= 0) {
                String columnName = terms.get(termPos);
                selectTerms.set(termPos, "\"" + columnName + "\"");
            }
        });
        String select = String.join(" ", terms.subList(0, fromPos));
        String from = String.join(" ", terms.subList(fromPos, wherePos));
        return select + " " + from;
    }

    /**
     * Generate a comprehensive SQL query to combine queries
     * in the plot.
     *
     * @param plot          plot to be rendered in the screen
     * @param length        number of queries are included in the plot
     *
     * @return              SQL query that combines all data points in the plot
     */
    public String combinedPredicate(Plot plot, int length) {
        int freeIndex = plot.freeIndex;
        int valueCtr = valueIndex.indexOf(freeIndex);
        String combinedPredicate;
        if (valueCtr >= 0) {
            int columnPos = columnIndex.get(valueCtr);
            String columnName = keyToTerms[columnPos][plot.dataPoints.get(0).vector[columnPos]];
            Stream<String> valuesStream = plot.dataPoints.subList(0, length).stream()
                    .map(dataPoint -> "'" + keyToTerms[freeIndex][dataPoint.vector[freeIndex]] + "'");
            String joinedValues = valuesStream.collect(Collectors.joining(","));
            combinedPredicate = "\"" + columnName + "\" IN (" + joinedValues + ")";
        }
        else {
            int valuePos = -1;
            int columnPos = -1;
            for (int columnCtr = 0; columnCtr < columnIndex.size(); columnCtr++) {
                int columnIndexVal = columnIndex.get(columnCtr);
                if (columnIndexVal == freeIndex) {
                    valuePos = replaceIndex.get(valueIndex.get(columnCtr));
                    columnPos = replaceIndex.get(columnIndexVal);
                    break;
                }
            }
            if (columnPos != -1) {
                String predicate = String.join("", terms.subList(columnPos + 2, valuePos + 2));
                Stream<String> columnsStream = plot.dataPoints.subList(0, length).stream()
                        .map(dataPoint -> "\"" + keyToTerms[freeIndex][dataPoint.vector[freeIndex]] + "\" " + predicate);
                combinedPredicate = columnsStream.collect(Collectors.joining(" OR "));

            }
            else {
                combinedPredicate = "";
                System.out.println("Cannot find the column!");
            }
        }
        return combinedPredicate;
    }

    /**
     * Initialize the index fields by parsing the SQL syntax.
     *
     * @param queryTemplate             SQL query parsed by Text-SQL model
     * @throws JSQLParserException
     */
    public void parseQueryTemplate(String queryTemplate) throws JSQLParserException {
        Select sqlStatement = (Select) CCJSqlParserUtil.parse(queryTemplate);
        PlainSelect plainSelect = (PlainSelect) sqlStatement.getSelectBody();
        List<SelectItem> selectItems = plainSelect.getSelectItems();
        FromItem fromItem = plainSelect.getFromItem();
        // Parse select items
        terms.add("SELECT");
        this.selectPos = 0;
        SelectReplaceVisitor selector = new SelectReplaceVisitor(terms, replaceIndex, selectIndex);
        for (SelectItem item: selectItems) {
            Expression selectExpr = ((SelectExpressionItem)item).getExpression();
            selectExpr.accept(selector);
        }
        // Parse from tables
        this.fromPos = terms.size();
        terms.add("FROM");
        if (fromItem instanceof Table) {
            Table table = (Table)fromItem;
            String dataset = table.getName();
            this.dataset = dataset;
            terms.add(dataset);
        }
        // Parse join tables
//        List<Join> joins = plainSelect.getJoins();
//        for (Join join: joins) {
//
//        }
        // Parse where clauses
        this.wherePos = terms.size();
        terms.add("WHERE");
        WhereReplaceVisitor whereReplaceVisitor =
                new WhereReplaceVisitor(terms, replaceIndex, valueIndex, columnIndex, joinIndex);
        plainSelect.getWhere().accept(whereReplaceVisitor);
    }
}
