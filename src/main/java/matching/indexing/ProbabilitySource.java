package matching.indexing;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;

import java.io.IOException;

public class ProbabilitySource extends DoubleValuesSource {
    @Override
    public DoubleValues getValues(LeafReaderContext leafReaderContext, DoubleValues doubleValues) throws IOException {
        return null;
    }

    @Override
    public boolean needsScores() {
        return false;
    }

    @Override
    public DoubleValuesSource rewrite(IndexSearcher indexSearcher) throws IOException {
        return null;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        return false;
    }

    @Override
    public String toString() {
        return null;
    }

    @Override
    public boolean isCacheable(LeafReaderContext leafReaderContext) {
        return false;
    }
}
