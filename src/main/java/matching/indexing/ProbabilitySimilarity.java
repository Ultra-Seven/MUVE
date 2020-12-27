package matching.indexing;

import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.similarities.BasicStats;

import org.apache.lucene.search.similarities.Similarity;

public class ProbabilitySimilarity extends Similarity {

    @Override
    public long computeNorm(FieldInvertState state) {
        return 0;
    }

    @Override
    public SimScorer scorer(float boost, CollectionStatistics collectionStats, TermStatistics... termStats) {
        return null;
    }

    private static class ProbScorer extends Similarity.SimScorer {

        @Override
        public float score(float freq, long norm) {
            return 0;
        }
    }
}

