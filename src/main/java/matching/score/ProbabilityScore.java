package matching.score;
import info.debatty.java.stringsimilarity.JaroWinkler;

public class ProbabilityScore {

    public static double score(String inputString, String hitString) {
        JaroWinkler jaroWinkler = new JaroWinkler();
        // Word spelling score.
        double stringScore = jaroWinkler.similarity(inputString.toLowerCase(), hitString.toLowerCase());
        // Semantic score

//        compareGraph(inputGraph, hitGraph);

        System.out.println("Example: dependency parse");
        return stringScore;
    }

    public static void main(String[] args) {
        System.out.println(score("5 Avenue", "58-55 54 Avenue"));
    }
}
