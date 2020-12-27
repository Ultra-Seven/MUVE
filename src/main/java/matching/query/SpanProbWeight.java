package matching.query;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.index.Terms;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.spans.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SpanProbWeight extends SpanWeight {

    final List<SpanWeight> subWeights;
    final SpanProbQuery query;
    public SpanProbWeight(List<SpanWeight> subWeights,
                          IndexSearcher searcher,
                          Map<Term, TermStates> terms,
                          float boost,
                          SpanProbQuery query) throws IOException {
        super(query, searcher, terms, boost);
        this.query = query;
        this.subWeights = subWeights;
    }

    @Override
    public void extractTermStates(Map<Term, TermStates> contexts) {
        for (SpanWeight w : subWeights) {
            w.extractTermStates(contexts);
        }
    }

    @Override
    public Spans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {

        Terms terms = context.reader().terms(field);
        if (terms == null) {
            return null; // field does not exist
        }

        ArrayList<Spans> subSpans = new ArrayList<>(query.clauses.size());
        for (SpanWeight w : subWeights) {
            Spans subSpan = w.getSpans(context, requiredPostings);
            if (subSpan != null) {
                subSpans.add(subSpan);
            } else {
                return null; // all required
            }
        }

        // all NearSpans require at least two subSpans
        return (!query.inOrder) ? new NearSpansUnordered(query.slop, subSpans)
                : new NearSpansOrdered(query.slop, subSpans);
    }

    @Override
    public void extractTerms(Set<Term> terms) {
        for (SpanWeight w : subWeights) {
            w.extractTerms(terms);
        }
    }

    @Override
    public boolean isCacheable(LeafReaderContext ctx) {
        for (Weight w : subWeights) {
            if (w.isCacheable(ctx) == false)
                return false;
        }
        return true;
    }

}