package matching.indexing;

import com.intuit.fuzzymatcher.component.Dictionary;
import org.apache.commons.lang3.StringUtils;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Preprocessing {
    public Preprocessing() {
    }

    public static Function<String, String> trim() {
        return StringUtils::trim;
    }

    public static Function<String, String> toLowerCase() {
        return StringUtils::lowerCase;
    }

    public static Function<String, String> numericValue() {
        return (str) -> str.replaceAll("[^0-9]", "");
    }

    public static Function<String, String> removeSpecialChars() {
        return (str) -> str.replaceAll("[^A-Za-z0-9 ]+", "");
    }

    public static Function<String, String> replaceSpecialChars(String delimiter) {
        return (str) -> str.replaceAll("[^A-Za-z0-9 ]+", delimiter);
    }

    public static Function<String, String> removeDomain() {
        return (str) -> {
            if (StringUtils.contains(str, "@")) {
                int index = str.indexOf(64);
                return str.substring(0, index);
            } else {
                return str;
            }
        };
    }

    public static Function<String, String> addressPreprocessing() {
        return (str) -> (String)replaceSpecialChars(" ").andThen(addressNormalization()).apply(str);
    }

    public static Function<String, String> namePreprocessing() {
        return (str) -> (String)removeTrailingNumber()
                .andThen(replaceSpecialChars(" "))
                .andThen(nameNormalization())
                .apply(str);
    }

    public static Function<String, String> addressNormalization() {
        return (str) -> Utils.getNormalizedString(str, Dictionary.addressDictionary);
    }

    public static Function<String, String> removeTrailingNumber() {
        return (str) -> str.replaceAll("\\d+$", "");
    }

    public static Function<String, String> nameNormalization() {
        return (str) -> Utils.getNormalizedString(str, Dictionary.nameDictionary);
    }

    public static Function<String, String> usPhoneNormalization() {
        return (str) -> (String)numericValue().andThen((s) -> s.length() == 10 ? "1" + s : s).apply(str);
    }

    public static Function<String, String> numberPreprocessing() {
        return (str) -> {
            Pattern pattern = Pattern.compile("-?\\d+(\\.\\d+)?");
            Matcher matcher = pattern.matcher(str);
            return matcher.find() ? matcher.group() : str;
        };
    }

    public static Function<String, String> none() {
        return (str) -> str;
    }
}
