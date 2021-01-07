package matching;

import expressions.PlainVisitor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.schema.Column;

import java.util.ArrayList;
import java.util.List;

public class SelectVisitor extends PlainVisitor {
    public final List<String> columns = new ArrayList<>();
    public final List<Integer> ops = new ArrayList<>();



    @Override
    public void visit(Function function) {
        String name = function.getName();
        switch (name) {
            case "count":
                ops.add(0);
                break;
            case "max":
                ops.add(1);
                break;
            case "min":
                ops.add(2);
                break;
            case "avg":
                ops.add(3);
                break;
            case "sum":
                ops.add(4);
                break;
            default:
                ops.add(5);
                break;
        }
        for (Expression expression: function.getParameters().getExpressions()) {
            expression.accept(this);
        }
    }

    @Override
    public void visit(Column column) {
        columns.add(column.getColumnName().replace("\"", ""));
    }
}
