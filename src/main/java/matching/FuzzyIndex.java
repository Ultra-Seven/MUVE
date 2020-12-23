package matching;

import matching.indexing.Indexer;
import matching.schema.Sample311;
import matching.schema.Schema;

import java.io.*;

public class FuzzyIndex {
    public static final String INDEX_DIR = "./index";
    public static final String PHONETIC_DIR = "./phonetic";
    public static final String DATA_FILE = "/Users/tracy/Documents/Research/mlsql/sample_311.csv";
    public static final String AU_FILE = "/Users/tracy/Documents/PythonProject/xls2csv/csv/sample_au.csv";
    public static final String FIELD_CONTENTS = "content";
    public static final String FIELD_PATH = "path";


    public static void main(String[] args) throws IOException {
        String inputFilePath = DATA_FILE;
        String[] name_arr = inputFilePath.split("/");
        Schema schema = new Sample311();

        String name = name_arr[name_arr.length - 1].split("\\.")[0];
        String outputDirPath = (Indexer.Phonetic ? PHONETIC_DIR : INDEX_DIR) + "/" + name;

        long startTime = System.currentTimeMillis();
        Indexer index  = new Indexer(inputFilePath, outputDirPath, schema);

        System.out.print("Creating index " + outputDirPath + " from " + inputFilePath + " ... ");
        index.insert();

        System.out.format("Finished in %.2f seconds.\n", (float) (System.currentTimeMillis() - startTime) / 1000);
    }
}
