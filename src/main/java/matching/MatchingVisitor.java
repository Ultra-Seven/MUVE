package matching;

import expressions.PlainVisitor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;

import java.util.ArrayList;
import java.util.List;

public class MatchingVisitor extends PlainVisitor {
    public final List<Column> columns = new ArrayList<>();

    public MatchingVisitor() {

    }

    @Override
    public void visit(OrExpression orExpression) {
        Expression leftExpression = orExpression.getLeftExpression();
        Expression rightExpression = orExpression.getLeftExpression();
        leftExpression.accept(this);
        rightExpression.accept(this);
    }

    @Override
    public void visit(AndExpression andExpression) {
        Expression leftExpression = andExpression.getLeftExpression();
        Expression rightExpression = andExpression.getLeftExpression();
        leftExpression.accept(this);
        rightExpression.accept(this);
    }


    @Override
    public void visit(EqualsTo equalsTo) {
        Expression leftExpression = equalsTo.getLeftExpression();
        if (leftExpression instanceof Column) {
            Column column = (Column) leftExpression;
            columns.add(column);
        }
    }
}
