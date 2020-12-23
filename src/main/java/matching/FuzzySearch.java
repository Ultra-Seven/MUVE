package matching;


import matching.indexing.Indexer;
import org.apache.commons.codec.language.DoubleMetaphone;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static matching.FuzzyIndex.*;

public class FuzzySearch {
    public static final int TOPK = 50;
    public static final Map<String, IndexSearcher> searchers = new ConcurrentHashMap<>();

    public static ScoreDoc[] search(String query_str, String dataset) throws IOException, ParseException {
//        Analyzer analyzer = new StandardAnalyzer();
//        Query query = parser.parse(query_str);
        Query query = new FuzzyQuery(new Term("content", query_str), 2);
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
        ScoreDoc[] hits = searcher.search(query, TOPK).scoreDocs;
        // Use phonetic indexing
        if (Indexer.Phonetic) {
            Map<Integer, ScoreDoc> idToDocs = new HashMap<>(hits.length);
            Arrays.stream(hits).forEach(doc -> idToDocs.put(doc.doc, doc));
            DoubleMetaphone encoder = new DoubleMetaphone();
            String representation = encoder.encode(query_str);
            Query phoneticQuery = new QueryParser(
                    "phonetic", new StandardAnalyzer()
            ).parse(representation);

//            Query phoneticQuery = new FuzzyQuery(
//                    new Term("phonetic", representation), 2);
            ScoreDoc[] phoneticHits = searcher.search(phoneticQuery, TOPK).scoreDocs;
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



    public static void main(String[] arg) throws IOException, ParseException {
        String query_str = "Rika";
        String dataset = "sample_au";
//        String query_str = "brooklyn";
        Query query = new FuzzyQuery(new Term("content", query_str), 2);
        IndexReader reader = null;
        String searchDir = (Indexer.Phonetic ? PHONETIC_DIR : INDEX_DIR) + "/" + dataset;
        reader = DirectoryReader.open(FSDirectory.open(Paths.get(searchDir)));
        IndexSearcher searcher = new IndexSearcher(reader);
        ScoreDoc[] hits = searcher.search(query, TOPK).scoreDocs;
        // Use phonetic indexing
        if (Indexer.Phonetic) {
            Map<Integer, ScoreDoc> idToDocs = new HashMap<>(hits.length);
            Arrays.stream(hits).forEach(doc -> idToDocs.put(doc.doc, doc));
            DoubleMetaphone encoder = new DoubleMetaphone();
            String representation = encoder.encode(query_str);
            Query phoneticQuery = new QueryParser(
                    "phonetic", new StandardAnalyzer()
            ).parse(representation);

//            Query phoneticQuery = new FuzzyQuery(
//                    new Term("phonetic", representation), 2);
            ScoreDoc[] phoneticHits = searcher.search(phoneticQuery, TOPK).scoreDocs;
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
            System.out.println("Column: " + column + "\tContent: " + content + "\tScore: " + scoreDoc.score);
        }
    }
}
