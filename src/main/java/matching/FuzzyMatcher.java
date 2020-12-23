package matching;


import com.intuit.fuzzymatcher.component.MatchService;
import com.intuit.fuzzymatcher.domain.Document;
import com.intuit.fuzzymatcher.domain.Element;
import com.intuit.fuzzymatcher.domain.ElementType;
import com.intuit.fuzzymatcher.domain.Match;
import com.intuit.fuzzymatcher.function.PreProcessFunction;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.intuit.fuzzymatcher.domain.ElementType.*;
import static com.intuit.fuzzymatcher.function.PreProcessFunction.*;

public class FuzzyMatcher {
    public static String INPUT_PATH_311 = "/Users/tracy/Documents/Research/mlsql/sample_311.csv";
    public static ElementType[] TYPES_311 = new ElementType[]{
            NUMBER, DATE, DATE, NAME, NAME, NAME, NAME, NAME, NUMBER,
            ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS,
            NAME, NAME, ADDRESS, NAME, NAME, DATE, TEXT, DATE,
            ADDRESS, NUMBER, NAME, NUMBER, NUMBER, NAME, NAME, NAME,
            NAME, NAME, ADDRESS, NAME, NAME, NAME, ADDRESS, NUMBER, NUMBER, NUMBER
    };
    public static String INPUT_PATH_AU = "/Users/tracy/Documents/PythonProject/xls2csv/csv/sample_au.csv";
    public static CSVParser csvParser;
    public static Function<String, String> replaceSpecialChars() {
        return (str) -> str.replaceAll("[^A-Za-z0-9 ]+", " ");
    }
    public static Function<String, String> customizedNamePreprocessing() {
        return (str) -> (String)PreProcessFunction
                .removeTrailingNumber()
                .andThen(replaceSpecialChars())
                .andThen(nameNormalization()).apply(str);
    }
    public static void main(String[] args) throws IOException {
        ElementType[] elementTypes = TYPES_311;
        String inputPath = INPUT_PATH_311;
        csvParser = CSVParser.parse(new File(inputPath),
                Charset.defaultCharset(), CSVFormat.RFC4180.withHeader());
        Iterator<CSVRecord> iterator = csvParser.iterator();
        List<Document> documentList = new ArrayList<>();
        while (iterator.hasNext()) {
            CSVRecord record = iterator.next();
            int nrEntries = record.size();
            Document.Builder builder = new Document.Builder(record.get(0));
            for (int entryCtr = 1; entryCtr < nrEntries; entryCtr++) {
                String entry = record.get(entryCtr);
                ElementType type = elementTypes[entryCtr];
                Element.Builder<String> stringBuilder = new Element.Builder<String>().setValue(entry);
                if (type == NAME) {
                    stringBuilder.setPreProcessingFunction(customizedNamePreprocessing());
                }
                builder.addElement(
                        stringBuilder.setType(elementTypes[entryCtr]).createElement()
                );
            }
            Document document = builder.createDocument();
            documentList.add(document);
        }

        MatchService matchService = new MatchService();
        Document document = new Document.Builder("1").createDocument();
        Map<String, List<Match<Document>>> result = matchService.applyMatchByDocId(document, documentList);

        result.entrySet().forEach(entry -> {
            entry.getValue().forEach(match -> {
                System.out.println("Data: " + match.getData() +
                        " Matched With: " + match.getMatchedWith() +
                        " Score: " + match.getScore().getResult());
            });
        });
    }
}
