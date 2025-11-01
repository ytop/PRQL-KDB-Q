package com.prqlq;

import java.util.*;

/**
 * Abstract Syntax Tree nodes for PRQL-Q
 */
public class ASTNode {
    
    // Base expression interface
    public interface Expression {
        Object evaluate(Map<String, Object> row, QueryContext context);
    }
    
    // Pipeline operation interface
    public interface Operation {
        List<Map<String, Object>> execute(List<Map<String, Object>> input, QueryContext context);
    }
    
    // Literal value
    public static class Literal implements Expression {
        private final Object value;
        
        public Literal(Object value) {
            this.value = value;
        }
        
        @Override
        public Object evaluate(Map<String, Object> row, QueryContext context) {
            return value;
        }
        
        public Object getValue() {
            return value;
        }
        
        @Override
        public String toString() {
            return "Literal(" + value + ")";
        }
    }
    
    // Variable reference
    public static class Variable implements Expression {
        private final String name;
        
        public Variable(String name) {
            this.name = name;
        }
        
        @Override
        public Object evaluate(Map<String, Object> row, QueryContext context) {
            if (row.containsKey(name)) {
                return row.get(name);
            }
            // Check if it's a nested field (e.g., table.field)
            if (name.contains(".")) {
                String[] parts = name.split("\\.");
                if (parts.length == 2 && row.containsKey(parts[1])) {
                    return row.get(parts[1]);
                }
            }
            return null;
        }
        
        public String getName() {
            return name;
        }
        
        @Override
        public String toString() {
            return "Variable(" + name + ")";
        }
    }
    
    // Binary operation
    public static class BinaryOp implements Expression {
        public enum Operator {
            ADD, SUBTRACT, MULTIPLY, DIVIDE,
            EQ, NE, LT, GT, LE, GE,
            AND, OR
        }
        
        private final Operator op;
        private final Expression left;
        private final Expression right;
        
        public BinaryOp(Operator op, Expression left, Expression right) {
            this.op = op;
            this.left = left;
            this.right = right;
        }
        
        @Override
        public Object evaluate(Map<String, Object> row, QueryContext context) {
            Object leftVal = left.evaluate(row, context);
            Object rightVal = right.evaluate(row, context);
            
            if (leftVal == null || rightVal == null) {
                if (op == Operator.EQ) return leftVal == rightVal;
                if (op == Operator.NE) return leftVal != rightVal;
                return null;
            }
            
            switch (op) {
                case ADD:
                    return toNumber(leftVal) + toNumber(rightVal);
                case SUBTRACT:
                    return toNumber(leftVal) - toNumber(rightVal);
                case MULTIPLY:
                    return toNumber(leftVal) * toNumber(rightVal);
                case DIVIDE:
                    return toNumber(leftVal) / toNumber(rightVal);
                case EQ:
                    return Objects.equals(leftVal, rightVal);
                case NE:
                    return !Objects.equals(leftVal, rightVal);
                case LT:
                    return compare(leftVal, rightVal) < 0;
                case GT:
                    return compare(leftVal, rightVal) > 0;
                case LE:
                    return compare(leftVal, rightVal) <= 0;
                case GE:
                    return compare(leftVal, rightVal) >= 0;
                case AND:
                    return toBoolean(leftVal) && toBoolean(rightVal);
                case OR:
                    return toBoolean(leftVal) || toBoolean(rightVal);
                default:
                    throw new RuntimeException("Unknown operator: " + op);
            }
        }
        
        private double toNumber(Object obj) {
            if (obj instanceof Number) {
                return ((Number) obj).doubleValue();
            }
            return Double.parseDouble(obj.toString());
        }
        
        private boolean toBoolean(Object obj) {
            if (obj instanceof Boolean) {
                return (Boolean) obj;
            }
            return obj != null && !obj.equals(0) && !obj.equals("");
        }
        
        @SuppressWarnings("unchecked")
        private int compare(Object left, Object right) {
            if (left instanceof Comparable && right instanceof Comparable) {
                return ((Comparable) left).compareTo(right);
            }
            return left.toString().compareTo(right.toString());
        }
        
        @Override
        public String toString() {
            return "BinaryOp(" + op + ", " + left + ", " + right + ")";
        }
    }
    
    // Unary operation
    public static class UnaryOp implements Expression {
        public enum Operator {
            NOT, NEGATE
        }
        
        private final Operator op;
        private final Expression expr;
        
        public UnaryOp(Operator op, Expression expr) {
            this.op = op;
            this.expr = expr;
        }
        
        @Override
        public Object evaluate(Map<String, Object> row, QueryContext context) {
            Object val = expr.evaluate(row, context);
            
            switch (op) {
                case NOT:
                    if (val instanceof Boolean) {
                        return !(Boolean) val;
                    }
                    return val == null || val.equals(0) || val.equals("");
                case NEGATE:
                    if (val instanceof Number) {
                        return -((Number) val).doubleValue();
                    }
                    throw new RuntimeException("Cannot negate non-number");
                default:
                    throw new RuntimeException("Unknown unary operator: " + op);
            }
        }
        
        @Override
        public String toString() {
            return "UnaryOp(" + op + ", " + expr + ")";
        }
    }
    
    // Function call
    public static class FunctionCall implements Expression {
        private final String name;
        private final List<Expression> arguments;
        
        public FunctionCall(String name, List<Expression> arguments) {
            this.name = name;
            this.arguments = arguments;
        }
        
        @Override
        public Object evaluate(Map<String, Object> row, QueryContext context) {
            List<Object> args = new ArrayList<>();
            for (Expression arg : arguments) {
                args.add(arg.evaluate(row, context));
            }
            return context.callFunction(name, args);
        }
        
        public String getName() {
            return name;
        }
        
        public List<Expression> getArguments() {
            return arguments;
        }
        
        @Override
        public String toString() {
            return "FunctionCall(" + name + ", " + arguments + ")";
        }
    }
    
    // Column specification for select
    public static class ColumnSpec {
        private final String alias;  // null if no alias
        private final Expression expression;
        private final boolean isWildcard;
        
        public ColumnSpec(String alias, Expression expression) {
            this.alias = alias;
            this.expression = expression;
            this.isWildcard = false;
        }
        
        public ColumnSpec(boolean isWildcard) {
            this.alias = null;
            this.expression = null;
            this.isWildcard = isWildcard;
        }
        
        public String getAlias() {
            return alias;
        }
        
        public Expression getExpression() {
            return expression;
        }
        
        public boolean isWildcard() {
            return isWildcard;
        }
        
        @Override
        public String toString() {
            if (isWildcard) return "ColumnSpec(*)";
            return "ColumnSpec(" + alias + ": " + expression + ")";
        }
    }
    
    // Filter operation: ?[condition]
    public static class FilterOp implements Operation {
        private final Expression condition;
        
        public FilterOp(Expression condition) {
            this.condition = condition;
        }
        
        @Override
        public List<Map<String, Object>> execute(List<Map<String, Object>> input, QueryContext context) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : input) {
                Object val = condition.evaluate(row, context);
                if (val instanceof Boolean && (Boolean) val) {
                    result.add(row);
                }
            }
            return result;
        }
        
        public Expression getCondition() {
            return condition;
        }
        
        @Override
        public String toString() {
            return "FilterOp(" + condition + ")";
        }
    }
    
    // Select operation: ![columns]
    public static class SelectOp implements Operation {
        private final List<ColumnSpec> columns;
        
        public SelectOp(List<ColumnSpec> columns) {
            this.columns = columns;
        }
        
        @Override
        public List<Map<String, Object>> execute(List<Map<String, Object>> input, QueryContext context) {
            List<Map<String, Object>> result = new ArrayList<>();
            
            for (Map<String, Object> row : input) {
                Map<String, Object> newRow = new LinkedHashMap<>();
                
                for (ColumnSpec col : columns) {
                    if (col.isWildcard()) {
                        newRow.putAll(row);
                    } else {
                        Object value = col.getExpression().evaluate(row, context);
                        String colName = col.getAlias() != null ? col.getAlias() : 
                                        (col.getExpression() instanceof Variable ? 
                                         ((Variable) col.getExpression()).getName() : "col");
                        newRow.put(colName, value);
                    }
                }
                
                result.add(newRow);
            }
            
            return result;
        }
        
        public List<ColumnSpec> getColumns() {
            return columns;
        }
        
        @Override
        public String toString() {
            return "SelectOp(" + columns + ")";
        }
    }
    
    // Sort operation: ^[col] or v[col]
    public static class SortOp implements Operation {
        private final String column;
        private final boolean ascending;
        
        public SortOp(String column, boolean ascending) {
            this.column = column;
            this.ascending = ascending;
        }
        
        @Override
        public List<Map<String, Object>> execute(List<Map<String, Object>> input, QueryContext context) {
            List<Map<String, Object>> result = new ArrayList<>(input);
            
            result.sort((a, b) -> {
                Object aVal = a.get(column);
                Object bVal = b.get(column);
                
                if (aVal == null && bVal == null) return 0;
                if (aVal == null) return ascending ? -1 : 1;
                if (bVal == null) return ascending ? 1 : -1;
                
                @SuppressWarnings("unchecked")
                int cmp = ((Comparable) aVal).compareTo(bVal);
                return ascending ? cmp : -cmp;
            });
            
            return result;
        }
        
        @Override
        public String toString() {
            return "SortOp(" + column + ", " + (ascending ? "ASC" : "DESC") + ")";
        }
    }
    
    // Group operation: @[columns]
    public static class GroupOp implements Operation {
        private final List<String> groupByColumns;
        
        public GroupOp(List<String> groupByColumns) {
            this.groupByColumns = groupByColumns;
        }
        
        @Override
        public List<Map<String, Object>> execute(List<Map<String, Object>> input, QueryContext context) {
            // Store grouped data in context for next operation (usually select with aggregates)
            Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
            
            for (Map<String, Object> row : input) {
                String key = buildGroupKey(row);
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
            }
            
            context.setGroups(groups);
            context.setGroupByColumns(groupByColumns);
            
            // Return one row per group with group keys
            List<Map<String, Object>> result = new ArrayList<>();
            for (List<Map<String, Object>> group : groups.values()) {
                if (!group.isEmpty()) {
                    Map<String, Object> groupRow = new LinkedHashMap<>();
                    for (String col : groupByColumns) {
                        groupRow.put(col, group.get(0).get(col));
                    }
                    context.setCurrentGroup(group);
                    result.add(groupRow);
                }
            }
            
            return result;
        }
        
        private String buildGroupKey(Map<String, Object> row) {
            StringBuilder key = new StringBuilder();
            for (String col : groupByColumns) {
                if (key.length() > 0) key.append("|");
                key.append(row.get(col));
            }
            return key.toString();
        }
        
        public List<String> getGroupByColumns() {
            return groupByColumns;
        }
        
        @Override
        public String toString() {
            return "GroupOp(" + groupByColumns + ")";
        }
    }
    
    // Limit operation: #[n]
    public static class LimitOp implements Operation {
        private final int limit;
        
        public LimitOp(int limit) {
            this.limit = limit;
        }
        
        @Override
        public List<Map<String, Object>> execute(List<Map<String, Object>> input, QueryContext context) {
            if (limit >= 0) {
                return input.subList(0, Math.min(limit, input.size()));
            } else {
                // Negative limit means take from end
                int start = Math.max(0, input.size() + limit);
                return input.subList(start, input.size());
            }
        }
        
        @Override
        public String toString() {
            return "LimitOp(" + limit + ")";
        }
    }
    
    // Drop operation: _[n]
    public static class DropOp implements Operation {
        private final int count;
        
        public DropOp(int count) {
            this.count = count;
        }
        
        @Override
        public List<Map<String, Object>> execute(List<Map<String, Object>> input, QueryContext context) {
            int start = Math.min(count, input.size());
            return input.subList(start, input.size());
        }
        
        @Override
        public String toString() {
            return "DropOp(" + count + ")";
        }
    }
    
    // Pipeline - chain of operations
    public static class Pipeline {
        private final String tableName;
        private final List<Operation> operations;
        
        public Pipeline(String tableName, List<Operation> operations) {
            this.tableName = tableName;
            this.operations = operations;
        }
        
        public String getTableName() {
            return tableName;
        }
        
        public List<Operation> getOperations() {
            return operations;
        }
        
        @Override
        public String toString() {
            return "Pipeline(" + tableName + ", " + operations + ")";
        }
    }
}
