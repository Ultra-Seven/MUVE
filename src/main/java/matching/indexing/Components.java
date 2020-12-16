package matching.indexing;

import org.apache.commons.codec.language.bm.NameType;
import org.apache.commons.codec.language.bm.PhoneticEngine;
import org.apache.commons.codec.language.bm.RuleType;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.core.LetterTokenizer;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.phonetic.BeiderMorseFilter;
import org.apache.lucene.analysis.phonetic.DoubleMetaphoneFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import java.io.Reader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Paths;

abstract class Components {
    public static class CSV {
        /**
         * Apache commons CSV parser.
         *
         * @param inputFilePath Path to the CSV file.
         * @return Apache commons CSV parser instance.
         * @throws IOException
         */
        public static CSVParser getCsvParser(String inputFilePath) throws IOException {
            return CSVParser.parse(new File(inputFilePath), Charset.defaultCharset(), CSVFormat.RFC4180.withHeader());
        }
    }

    public static class Lucene {
        /**
         * Creates and initializes new instance of Document class.
         *
         * @return Lucene's Document instance.
         */
        public static Document getEmptyDocument() {
            return new Document();
        }

        /**
         * Creates and initializes a new instance of Lucene's IndexWriter class in CREATE open mode.
         *
         * @param outputDirPath Path to directory where the index is going to be created.
         * @return Initialized IndexWriter instance.
         * @throws IOException
         */
        public static IndexWriter getIndexWriter(String outputDirPath) throws IOException {
            Directory outputDir = FSDirectory.open(Paths.get(outputDirPath));
            Analyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig config = new IndexWriterConfig(analyzer).setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            return new IndexWriter(outputDir, config);
        }

        /**
         * Creates and initializes a new instance of Lucene's IndexWriter class.
         *
         * @param outputDirPath Path to directory where the index is going to be created.
         * @param mode Open mode in which the index will be accesses.
         * @return Initialized IndexWriter instance.
         * @throws IOException
         */
        public static IndexWriter getIndexWriter(String outputDirPath, Indexer.OpenMode mode) throws IOException {
            IndexWriterConfig.OpenMode luceneOpenMode;

            if (mode == Indexer.OpenMode.CREATE_OR_APPEND) {
                luceneOpenMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND;
            } else if (mode == Indexer.OpenMode.APPEND) {
                luceneOpenMode = IndexWriterConfig.OpenMode.APPEND;
            } else {
                luceneOpenMode = IndexWriterConfig.OpenMode.CREATE;
            }

            Directory outputDir = FSDirectory.open(Paths.get(outputDirPath));

            Analyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig config = new IndexWriterConfig(analyzer).setOpenMode(luceneOpenMode);

            return new IndexWriter(outputDir, config);
        }
    }

    /**
     * Creates an instance of the OutputWriter class in CREATE open mode.
     *
     * @param outputDirPath Path to dir where the index is going to be stored.
     * @return OutputWriter instance.
     * @throws IOException
     */
    public static OutputWriter getOutputWriter(String outputDirPath) throws IOException {
        return new OutputWriter(outputDirPath);
    }

    /**
     * Creates an instance of the OutputWriter class in specified open mode.
     *
     * @param outputDirPath Path to directory where the index is going to be created.
     * @param mode Open mode in which the index will be accesses.
     * @return OutputWriter instance.
     * @throws IOException
     */
    public static OutputWriter getOutputWriter(String outputDirPath, Indexer.OpenMode mode) throws IOException {
        return new OutputWriter(outputDirPath, mode);
    }

    /**
     * Creates an InputReader instance.
     *
     * @param inputFilePath Path to the input file.
     * @return InputReader instance.
     * @throws IOException
     */
    public static InputReader getInputReader(String inputFilePath) throws IOException {
        return new InputReader(inputFilePath);
    }
}
