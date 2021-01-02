package planning.viz;

import config.PlanConfig;
import matching.FuzzySearch;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.ScoreDoc;

import java.io.IOException;

public class PlannerTest {
    public static void main(String[] args) throws IOException, ParseException {
        String dataset = "sample_311";
        ScoreDoc[] docs = FuzzySearch.search("brockley", dataset);
        GreedyPlanner.plan(docs, 3, PlanConfig.R, FuzzySearch.searchers.get(dataset));
    }
}
