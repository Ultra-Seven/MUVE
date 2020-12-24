package matching;


import config.PlanConfig;
import matching.indexing.Indexer;
import org.apache.commons.codec.language.DoubleMetaphone;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static matching.FuzzyIndex.*;

public class FuzzySearch {
    public static final Map<String, IndexSearcher> searchers = new ConcurrentHashMap<>();

    public static ScoreDoc[] search(String query_str, String dataset) throws IOException, ParseException {
        String normalized = preprocessing(query_str);
        Query query = buildTermsQuery(normalized);
        IndexReader reader;
        IndexSearcher searcher;
        if (!searchers.containsKey(dataset)) {
            String searchDir = (Indexer.Phonetic ? PHONETIC_DIR : INDEX_DIR) + "/" + dataset;
            reader = DirectoryReader.open(FSDirectory.open(Paths.get(searchDir)));
            searcher = new IndexSearcher(reader);
            searchers.put(dataset, searcher);
        }
        else {
            searcher = searchers.get(dataset);
        }
        ScoreDoc[] hits = searcher.search(query, PlanConfig.TOPK).scoreDocs;
        // Use phonetic indexing
        if (Indexer.Phonetic) {
            Map<Integer, ScoreDoc> idToDocs = new HashMap<>(hits.length);
            Arrays.stream(hits).forEach(doc -> idToDocs.put(doc.doc, doc));
            Query phoneticQuery = buildPhoneticQuery(normalized);
            ScoreDoc[] phoneticHits = searcher.search(phoneticQuery, PlanConfig.TOPK).scoreDocs;
            for (ScoreDoc phoneticDoc : phoneticHits) {
                int docID = phoneticDoc.doc;
                if (idToDocs.containsKey(docID)) {
                    idToDocs.get(docID).score += phoneticDoc.score;
                }
                else {
                    idToDocs.put(docID, phoneticDoc);
                }
            }
            hits = idToDocs.values().stream().sorted(
                    Comparator.comparingDouble(doc -> -1 * doc.score)
            ).toArray(ScoreDoc[]::new);
        }
        return hits;
    }

    public static String preprocessing(String query_str) {
        return query_str.toLowerCase()
                .replaceAll("(?<=\\d)(rd|st|nd|th)\\b", "");
    }

    public static String phoneticEncoding(String query_str) {
        StringBuilder representation = new StringBuilder();
        DoubleMetaphone encoder = new DoubleMetaphone();
        for (String token: query_str.split(" ")) {
            if (!token.equals("")) {
                String encoding = encoder.encode(token);
                representation.append(encoding);
            }
        }
        return representation.toString();
    }

    public static Query buildTermsQuery(String query_str) {
        String[] tokens = query_str.split(" ");
        if (tokens.length == 1) {
            return new FuzzyQuery(
                    new Term("content", tokens[0]), 2);
        }
        else {
            SpanQuery[] clauses = new SpanQuery[tokens.length];
            for (int tokenCtr = 0; tokenCtr < tokens.length; tokenCtr++) {
                FuzzyQuery fuzzyQuery = new FuzzyQuery(
                        new Term("content", tokens[tokenCtr]), 2);
                clauses[tokenCtr] = new SpanMultiTermQueryWrapper<>(fuzzyQuery);

            }
            return new SpanNearQuery(clauses, 1, true);
        }
    }

    public static Query buildPhoneticQuery(String query_str) {
        String[] tokens = query_str.split(" ");
        DoubleMetaphone encoder = new DoubleMetaphone();
        List<FuzzyQuery> fuzzyQueries = new ArrayList<>(tokens.length);
        List<SpanQuery> clauses = new ArrayList<>(tokens.length);
        for (int tokenCtr = 0; tokenCtr < tokens.length; tokenCtr++) {
            String token = tokens[tokenCtr];
            if (!token.equals("")) {
                String encoding = encoder.encode(token);
                if (!encoding.equals("")) {
                    FuzzyQuery fuzzyQuery = new FuzzyQuery(
                            new Term("phonetic", encoding.toLowerCase()), 2);
                    fuzzyQueries.add(fuzzyQuery);
                    clauses.add(new SpanMultiTermQueryWrapper<>(fuzzyQuery));
                }
            }
        }
        if (fuzzyQueries.size() == 1) {
            return fuzzyQueries.get(0);
        }
        else {
            return new SpanNearQuery(clauses.toArray(new SpanQuery[0]), 3, true);
        }
    }

    public static void main(String[] arg) throws IOException, ParseException {
        String query_str = "5th AVENUE";
        String dataset = "sample_311";
        query_str = preprocessing(query_str);
//        String query_str = "brooklyn";
        Query query = buildTermsQuery(query_str);
        IndexReader reader = null;
        String searchDir = (Indexer.Phonetic ? PHONETIC_DIR : INDEX_DIR) + "/" + dataset;
        reader = DirectoryReader.open(FSDirectory.open(Paths.get(searchDir)));
        IndexSearcher searcher = new IndexSearcher(reader);
//        searcher.setSimilarity(new BM25Similarity(0, 0.75F));
        ScoreDoc[] hits = searcher.search(query, PlanConfig.TOPK).scoreDocs;
        Explanation explanation = searcher.explain(query, hits[0].doc);

        // Use phonetic indexing
        if (Indexer.Phonetic) {
            Map<Integer, ScoreDoc> idToDocs = new HashMap<>(hits.length);
            Arrays.stream(hits).forEach(doc -> idToDocs.put(doc.doc, doc));

            Query phoneticQuery = buildPhoneticQuery(query_str);
            ScoreDoc[] phoneticHits = searcher.search(phoneticQuery, PlanConfig.TOPK).scoreDocs;
            for (ScoreDoc phoneticDoc : phoneticHits) {
                int docID = phoneticDoc.doc;
                if (idToDocs.containsKey(docID)) {
                    idToDocs.get(docID).score += phoneticDoc.score;
                }
                else {
                    idToDocs.put(docID, phoneticDoc);
                }
            }
            hits = idToDocs.values().stream().sorted(
                    Comparator.comparingDouble(doc -> -1 * doc.score)
            ).toArray(ScoreDoc[]::new);
        }
        // Iterate through the results:
        for (int i = 0; i < hits.length; i++) {
            ScoreDoc scoreDoc = hits[i];
            Document hitDoc = searcher.doc(hits[i].doc);
            String column = hitDoc.get("column");
            String content = hitDoc.get("content");
            String phonetic = hitDoc.get("phonetic");
            String text = hitDoc.get("text");
            System.out.println("Column: " + column + "\tContent: " +
                    content + "\tPhonetic: " + phonetic + "\tText: " + text + "\tScore: " + scoreDoc.score);
        }
    }
}
