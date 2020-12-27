package matching.query;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.*;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanWeight;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SpanProbQuery extends SpanQuery implements Cloneable {
    public List<SpanQuery> clauses;
    public int slop;
    public boolean inOrder;
    public String field;
    /** Construct a SpanProbQuery.  Matches spans matching a span from each
     * clause, with up to <code>slop</code> total unmatched positions between
     * them.
     * <br>When <code>inOrder</code> is true, the spans from each clause
     * must be in the same order as in <code>clauses</code> and must be non-overlapping.
     * <br>When <code>inOrder</code> is false, the spans from each clause
     * need not be ordered and may overlap.
     * @param clausesIn the clauses to find near each other, in the same field, at least 2.
     * @param slop The slop value
     * @param inOrder true if order is important
     */
    public SpanProbQuery(SpanQuery[] clausesIn, int slop, boolean inOrder) {
        this.clauses = new ArrayList<>(clausesIn.length);
        for (SpanQuery clause : clausesIn) {
            if (this.field == null) {                               // check field
                this.field = clause.getField();
            } else if (clause.getField() != null && !clause.getField().equals(field)) {
                throw new IllegalArgumentException("Clauses must have same field.");
            }
            this.clauses.add(clause);
        }
        this.slop = slop;
        this.inOrder = inOrder;
    }

    /** Return the clauses whose spans are matched. */
    public SpanQuery[] getClauses() {
        return clauses.toArray(new SpanQuery[0]);
    }

    public int getSlop() { return slop; }

    public boolean isInOrder() { return inOrder; }

    @Override
    public String getField() {
        return field;
    }

    @Override
    public String toString(String field) {
        StringBuilder buffer = new StringBuilder();
        buffer.append("spanProb([");
        Iterator<SpanQuery> i = clauses.iterator();
        while (i.hasNext()) {
            SpanQuery clause = i.next();
            buffer.append(clause.toString(field));
            if (i.hasNext()) {
                buffer.append(", ");
            }
        }
        buffer.append("], ");
        buffer.append(slop);
        buffer.append(", ");
        buffer.append(inOrder);
        buffer.append(")");
        return buffer.toString();
    }

    @Override
    public SpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        List<SpanWeight> subWeights = new ArrayList<>();
        for (SpanQuery q : clauses) {
            subWeights.add(q.createWeight(searcher, scoreMode, boost));
        }
        return new SpanProbWeight(
                subWeights,
                searcher,
                scoreMode.needsScores() ? getTermStates(subWeights) : null,
                boost,
                this
        );
    }

    @Override
    public Query rewrite(IndexReader reader) throws IOException {
        boolean actuallyRewritten = false;
        List<SpanQuery> rewrittenClauses = new ArrayList<>();
        for (int clauseCtr = 0 ; clauseCtr < clauses.size(); clauseCtr++) {
            SpanQuery c = clauses.get(clauseCtr);
            SpanQuery query = (SpanQuery) c.rewrite(reader);
            actuallyRewritten |= query != c;
            rewrittenClauses.add(query);
        }
        if (actuallyRewritten) {
            try {
                SpanProbQuery rewritten = (SpanProbQuery) clone();
                rewritten.clauses = rewrittenClauses;
                return rewritten;
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
        return super.rewrite(reader);
    }

    @Override
    public void visit(QueryVisitor visitor) {
        if (visitor.acceptField(getField()) == false) {
            return;
        }
        QueryVisitor v = visitor.getSubVisitor(BooleanClause.Occur.MUST, this);
        for (SpanQuery clause : clauses) {
            clause.visit(v);
        }
    }

    @Override
    public boolean equals(Object obj) {
        return sameClassAs(obj) &&
                equalsTo(getClass().cast(obj));
    }

    private boolean equalsTo(SpanProbQuery other) {
        return inOrder == other.inOrder &&
                slop == other.slop &&
                clauses.equals(other.clauses);
    }

    @Override
    public int hashCode() {
        int result = classHash();
        result ^= clauses.hashCode();
        result += slop;
        int fac = 1 + (inOrder ? 8 : 4);
        return fac * result;
    }
}
