package matching.indexing;

import com.intuit.fuzzymatcher.exception.MatchException;
import org.apache.lucene.analysis.ngram.NGramTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Utils {
    public Utils() {
    }

    public static Stream<String> getNGrams(String value, int size) {
        Stream.Builder<String> stringStream = Stream.builder();
        if (value.length() <= size) {
            stringStream.add(value);
        } else {
            NGramTokenizer nGramTokenizer = new NGramTokenizer(size, size);
            CharTermAttribute charTermAttribute = (CharTermAttribute)nGramTokenizer.addAttribute(CharTermAttribute.class);
            nGramTokenizer.setReader(new StringReader(value));

            try {
                nGramTokenizer.reset();

                while(nGramTokenizer.incrementToken()) {
                    stringStream.add(charTermAttribute.toString());
                }

                nGramTokenizer.end();
                nGramTokenizer.close();
            } catch (IOException var6) {
                throw new MatchException("Failure in creating tokens : ", var6);
            }
        }

        return stringStream.build();
    }

    public static String getNormalizedString(String str, Map<String, String> dict) {
        return (String) Arrays.stream(str.split("\\s+")).map((d) -> {
            return dict.containsKey(d.toLowerCase()) ? (String)dict.get(d.toLowerCase()) : d;
        }).collect(Collectors.joining(" "));
    }

    public static boolean isNumeric(String str) {
        return str.matches(".*\\d.*");
    }
}