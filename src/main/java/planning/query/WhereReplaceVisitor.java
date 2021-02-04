package planning.query;

import expressions.PlainVisitor;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.schema.Column;

import java.util.List;

/**
 * Pare predicates in the SQL query.
 * Generally, the replaceable items
 * will be extracted from the predicates.
 *
 * @author Ziyun Wei
 */
public class WhereReplaceVisitor extends PlainVisitor {
    public final List<String> terms;
    public final List<Integer> replaceableIndex;
    public final List<Integer> valueIndex;
    public final List<Integer> columnIndex;
    public final List<Integer> joinIndex;
    private int lastColumnIndex = -1;
    public WhereReplaceVisitor(List<String> terms,
                               List<Integer> replaceableIndex,
                               List<Integer> valueIndex,
                               List<Integer> columnIndex,
                               List<Integer> joinIndex) {
        this.terms = terms;
        this.replaceableIndex = replaceableIndex;
        this.valueIndex = valueIndex;
        this.columnIndex = columnIndex;
        this.joinIndex = joinIndex;
    }
    @Override
    public void visit(AndExpression andExpression) {
        andExpression.getLeftExpression().accept(this);
        andExpression.getRightExpression().accept(this);
    }

    @Override
    public void visit(OrExpression orExpression) {
        orExpression.getLeftExpression().accept(this);
        orExpression.getRightExpression().accept(this);
    }

    @Override
    public void visit(EqualsTo equalsTo) {
        Expression leftExpression = equalsTo.getLeftExpression();
        Expression rightExpression = equalsTo.getRightExpression();
        leftExpression.accept(this);
        int leftColumnPos = lastColumnIndex;
        this.terms.add("=");
        rightExpression.accept(this);
        int rightColumnPos = lastColumnIndex;
        if (leftExpression instanceof Column && rightExpression instanceof Column) {
            joinIndex.add(leftColumnPos);
            joinIndex.add(rightColumnPos);
        }

    }

    @Override
    public void visit(GreaterThan greaterThan) {
        greaterThan.getLeftExpression().accept(this);
        this.terms.add(">");
        greaterThan.getRightExpression().accept(this);
    }

    @Override
    public void visit(GreaterThanEquals greaterThanEquals) {
        greaterThanEquals.getLeftExpression().accept(this);
        this.terms.add(">=");
        greaterThanEquals.getRightExpression().accept(this);
    }

    @Override
    public void visit(MinorThan minorThan) {
        minorThan.getLeftExpression().accept(this);
        this.terms.add("<");
        minorThan.getRightExpression().accept(this);
    }

    @Override
    public void visit(MinorThanEquals minorThanEquals) {
        minorThanEquals.getLeftExpression().accept(this);
        this.terms.add("<=");
        minorThanEquals.getRightExpression().accept(this);
    }

    @Override
    public void visit(DoubleValue doubleValue) {
        terms.add(doubleValue.toString());
    }

    @Override
    public void visit(LongValue longValue) {
        terms.add(longValue.toString());
    }

    @Override
    public void visit(StringValue stringValue) {
        String stringName = stringValue.toString().replace("'", "");
        terms.add("'");
        int nextIndex = terms.size();
        terms.add(stringName);
        int valuePos = replaceableIndex.size();
        replaceableIndex.add(nextIndex);
        valueIndex.add(valuePos);
        columnIndex.add(lastColumnIndex);
        terms.add("'");
    }

    @Override
    public void visit(Column column) {
        String columnName = column.getColumnName().replace("\"", "");
        terms.add("\"");
        int nextIndex = terms.size();
        terms.add(columnName);
        int columnPos = replaceableIndex.size();
        replaceableIndex.add(nextIndex);
        lastColumnIndex = columnPos;
        terms.add("\"");
    }
}
