package matching.score;
import info.debatty.java.stringsimilarity.JaroWinkler;

/**
 * Measure th score of each query. We consider
 * the string similarity. The score is defined by the
 * Jaro–Winkler distance.
 *
 * @author Ziyun Wei
 */
public class ProbabilityScore {

    public static double score(String inputString, String hitString) {
        JaroWinkler jaroWinkler = new JaroWinkler();
        String inputLowCase = inputString.toLowerCase().replace(" ", "");
        String hitLowCase = hitString.toLowerCase().replace(" ", "");

        // Word spelling score.
        double stringScore = jaroWinkler.similarity(inputLowCase, hitLowCase);

        // Semantic score

//        compareGraph(inputGraph, hitGraph);

        return stringScore;
    }

    public static void main(String[] args) {
        System.out.println(score("5 Avenue", "58-55 54 Avenue"));
    }
}
