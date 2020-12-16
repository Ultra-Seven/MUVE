package matching.indexing;

import org.apache.commons.codec.language.DoubleMetaphone;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.*;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;

class InputReader implements Iterator<Document[]> {
    /**
     * Apache commons csv parser.
     */
    private final CSVParser input;

    /**
     * Lucene's document object.
     * We are reusing single document object for obvious performance reasons.
     */
    private Document[] documents;

    /**
     * An array of fields. It's a de facto schema of the index.
     */
    private Field[] fields;
    /**
     * An array of fields. It's a de facto schema of the phonetic index.
     */
    private Field[] phonetics;

    /**
     * CSV column names extracted from the csv file header.
     */
    private String[] fieldNames;

    /**
     * Use iterator to read the contents of the CSV file.
     */
    private Iterator<CSVRecord> iterator;

    /**
     * An array of entry set. It contains distinct values for each column.
     */
    public Set<String>[] contents;
    /**
     * Encode string by using double metaphone.
     */
    public DoubleMetaphone metaphone = new DoubleMetaphone();

    /**
     * Initializes CSV file reader, document object and fields.
     *
     * @param inputFilePath Path to the CSV file that is going to be indexed.
     * @throws IOException
     */
    public InputReader(String inputFilePath) throws IOException {
        this.input = Components.CSV.getCsvParser(inputFilePath);
        this.iterator = input.iterator();

        this.initFieldNames();
        this.initDocumentFields();
    }

    /**
     * Closes the input stream.
     *
     * @throws IOException
     */
    public void close() throws IOException {
        this.input.close();
    }

    /**
     * Gets a CSV record and loads the values in the document fields.
     *
     * @return Document with the fields loaded from the CSV.
     */
    @Override
    public Document[] next() {
        int nrFields = this.fields.length;

        CSVRecord row = this.iterator.next();

        for (int fieldCtr = 0; fieldCtr < nrFields; fieldCtr++) {
            String value = row.get(fieldCtr);
            if (!this.contents[fieldCtr].contains(value)) {
                this.fields[fieldCtr].setStringValue(value);
                this.contents[fieldCtr].add(value);
                if (Indexer.Phonetic) {
                    StandardTokenizer stream = new StandardTokenizer();
                    stream.setReader(new StringReader(value));
                    CharTermAttribute charTermAttribute = stream.addAttribute(CharTermAttribute.class);
                    StringBuilder phonetic = new StringBuilder();
                    try {
                        stream.reset();
                        while (stream.incrementToken()) {
                            String term = charTermAttribute.toString();
                            String encoding = metaphone.encode(term);
                            if (!encoding.equals("")) {
                                if (!phonetic.toString().equals("")) {
                                    phonetic.append(" ");
                                }
                                phonetic.append(encoding);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    this.phonetics[fieldCtr].setStringValue(phonetic.toString());
                }
            }
            else {
                this.fields[fieldCtr].setStringValue("NULL");
            }
        }
        return documents;
    }

    /**
     * Checks if the CSV file has more contents to process.
     *
     * @return True if the iterator has next element.
     */
    @Override
    public boolean hasNext() {
        return this.iterator.hasNext();
    }

    /**
     * Not used by this class.
     */
    @Override
    public void remove() {}

    /**
     * Initializes filed names from the CSV file header.
     */
    private void initFieldNames() throws IOException {
        Object[] header = this.input.getHeaderMap().keySet().toArray();

        if (header.length == 0) throw new IOException("CSV file doesn't have headers");

        this.fieldNames = Arrays.copyOf(header, header.length, String[].class);
        this.fields = new Field[this.fieldNames.length];
        this.phonetics = new Field[this.fieldNames.length];
        this.contents = new HashSet[this.fieldNames.length];
        this.documents = new Document[this.fieldNames.length];
        for (int fieldCtr = 0; fieldCtr < this.fieldNames.length; fieldCtr++) {
            this.contents[fieldCtr] = new HashSet<>();
            this.documents[fieldCtr] = Components.Lucene.getEmptyDocument();
        }
    }

    /**
     * Initialized the document with empty fields. The field names are the column names in the CSV file.
     */
    private void initDocumentFields() {
        for (int fieldCtr = 0; fieldCtr < this.fieldNames.length; fieldCtr++) {
            this.fields[fieldCtr] = new TextField("content", "", Field.Store.YES);
            if (Indexer.Phonetic) {
                this.phonetics[fieldCtr] = new TextField("phonetic", "", Field.Store.YES);
                this.documents[fieldCtr].add(this.phonetics[fieldCtr]);
            }
            this.documents[fieldCtr].add(new StringField("column", fieldNames[fieldCtr], Field.Store.YES));
            this.documents[fieldCtr].add(this.fields[fieldCtr]);
        }
    }
}
