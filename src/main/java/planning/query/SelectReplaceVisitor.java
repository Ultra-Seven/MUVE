package planning.query;

import expressions.PlainVisitor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.schema.Column;
import java.util.List;

/**
 * Parse the select items.
 *
 * @author Ziyun Wei
 */
public class SelectReplaceVisitor extends PlainVisitor {
    public final List<String> terms;
    public final List<Integer> replaceableIndex;
    public final List<Integer> selectIndex;

    public SelectReplaceVisitor(List<String> terms,
                                List<Integer> replaceableIndex,
                                List<Integer> selectIndex) {
        this.terms = terms;
        this.replaceableIndex = replaceableIndex;
        this.selectIndex = selectIndex;
    }
    @Override
    public void visit(Function function) {
        String name = function.getName();
        terms.add(name);
        terms.add("(");
        if (function.getParameters() != null) {
            for (Expression expression: function.getParameters().getExpressions()) {
                expression.accept(this);
            }
        }
        else {
            terms.add("*");
        }
        terms.add(")");
    }

    @Override
    public void visit(Column column) {
        String columnName = column.getColumnName().replace("\"", "");
        int nextIndex = terms.size();
        terms.add(columnName);
        selectIndex.add(replaceableIndex.size());
        replaceableIndex.add(nextIndex);
    }
}
