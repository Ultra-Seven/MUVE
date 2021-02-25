package planning.query;

import java.util.Arrays;

public class SearchDocument implements Comparable<SearchDocument>{
    public final int[] documentIndices;
    public final double[] scores;

    public SearchDocument(int[] documentIndices, double[] scores) {
        this.documentIndices = documentIndices.clone();
        this.scores = scores.clone();
    }

    @Override
    public boolean equals(Object o) {
        // If the object is compared with itself then return true
        if (o == this) {
            return true;
        }

        /* Check if o is an instance of Complex or not
          "null instanceof [type]" also returns false */
        if (!(o instanceof SearchDocument)) {
            return false;
        }

        // typecast o to Complex so that we can compare data members
        SearchDocument c = (SearchDocument) o;

        // Compare the data members and return accordingly
        for (int index = 0; index < documentIndices.length; index++) {
            if (documentIndices[index] != c.documentIndices[index]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(documentIndices);
    }


    @Override
    public int compareTo(SearchDocument o) {
        double score = 0;
        double newScore = 0;
        for (int index = 0; index < documentIndices.length; index++) {
            score += scores[index];
            newScore += o.scores[index];
        }
        return Double.compare(score, newScore);
    }
}
