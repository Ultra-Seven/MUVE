package matching.indexing;

import matching.schema.Schema;
import org.apache.lucene.document.Document;

import java.io.IOException;

public class Indexer {
    /**
     * Open modes that the Indexer class supports
     */
    public enum OpenMode { CREATE, APPEND, CREATE_OR_APPEND }
    /**
     * Whether to create phonetic indexes.
     */
    public final static boolean Phonetic = true;

    /**
     * This instance reads the input file. Use Components to create a new instance.
     */
    private final InputReader input;

    /**
     * This instance creates the index. Use Components to create a new instance.
     */
    private final OutputWriter output;

    /**
     * Initializes the input, creates an output instance in CREATE open mode.
     *
     * @param inputFilePath Path to the file that is going to be indexed.
     * @param outputDirPath Path to the directory where the index will be stored.
     * @throws IOException
     */
    public Indexer(String inputFilePath, String outputDirPath, Schema schema) throws IOException {
        this.input  = Components.getInputReader(inputFilePath, schema);
        this.output = Components.getOutputWriter(outputDirPath);
    }

    /**
     * Takes a next entry from the input file and adds the entry to the index.
     *
     * @throws IOException
     */
    public void insert() throws IOException {
        while (this.input.hasNext()) {
            Document[] documents = this.input.next();
            this.output.insert(documents);
        }

        this.output.close();
    }

    /**
     * Takes a next entry from the input file and tries to update an existing document in the index.
     * If an existing document cannot be found for update, a new document will be added to the index.
     *
     * @throws IOException
     */
    public void update() throws IOException {
//        while (this.input.hasNext()) {
//            this.output.update(this.input.next());
//        }

        this.output.close();
    }

    /**
     * Removes the contents of an index.
     *
     * @param outputDirPath Path to the directory where the index will be stored.
     * @throws IOException
     */
    public static void drop(String outputDirPath) throws IOException {
        OutputWriter output = Components.getOutputWriter(outputDirPath);

        output.drop();
        output.close();
    }

    /**
     * Closes the input and the output resources.
     *
     * @throws IOException
     */
    public void close() throws IOException {
        this.input.close();
        this.output.close();
    }
}
