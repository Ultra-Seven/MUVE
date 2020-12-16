package matching;

import matching.indexing.Indexer;

import java.io.*;

public class FuzzyIndex {
    public static final String INDEX_DIR = "./index";
    public static final String PHONETIC_DIR = "./phonetic";
    public static final String DATA_FILE = "/Users/tracy/Documents/Research/mlsql/sample_311.csv";
    public static final String FIELD_CONTENTS = "content";
    public static final String FIELD_PATH = "path";


    public static void main(String[] args) throws IOException {
        String dir = Indexer.Phonetic ? PHONETIC_DIR : INDEX_DIR;
        Operator.create(DATA_FILE, dir);
    }
}
