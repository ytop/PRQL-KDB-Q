package com.prqlq;

import com.prqlq.ASTNode.*;
import com.prqlq.QueryPlanner.*;

import java.util.*;

/**
 * Query executor - executes the optimized query plan
 */
public class QueryExecutor {
    
    private final QueryContext context;
    
    public QueryExecutor(QueryContext context) {
        this.context = context;
    }
    
    /**
     * Execute a pipeline directly
     */
    public List<Map<String, Object>> execute(Pipeline pipeline) {
        // Get initial table
        List<Map<String, Object>> result = context.getTable(pipeline.getTableName());
        
        // Execute each operation in sequence
        for (Operation op : pipeline.getOperations()) {
            result = op.execute(result, context);
        }
        
        return result;
    }
    
    /**
     * Execute an optimized execution plan
     */
    public List<Map<String, Object>> execute(ExecutionPlan plan) {
        return executePlanNode(plan.getRoot(), null);
    }
    
    private List<Map<String, Object>> executePlanNode(PlanNode node, List<Map<String, Object>> input) {
        // Handle table scan (root)
        if (node.getType() == PlanNode.NodeType.TABLE_SCAN) {
            String tableName = (String) node.getMetadata().get("table");
            input = context.getTable(tableName);
        }
        
        // Execute this node's operation
        if (node.getOperation() != null) {
            input = executeOperation(node, input);
        }
        
        // Execute children (pipeline is linear, so only one child)
        for (PlanNode child : node.getChildren()) {
            input = executePlanNode(child, input);
        }
        
        return input;
    }
    
    private List<Map<String, Object>> executeOperation(PlanNode node, List<Map<String, Object>> input) {
        Operation op = node.getOperation();
        
        if (op == null) {
            return input;
        }
        
        // Add execution metadata
        long startTime = System.nanoTime();
        int inputSize = input.size();
        
        // Execute the operation
        List<Map<String, Object>> result = op.execute(input, context);
        
        // Record statistics
        long endTime = System.nanoTime();
        double executionTimeMs = (endTime - startTime) / 1_000_000.0;
        int outputSize = result.size();
        
        node.getMetadata().put("input_rows", inputSize);
        node.getMetadata().put("output_rows", outputSize);
        node.getMetadata().put("execution_time_ms", executionTimeMs);
        node.getMetadata().put("selectivity", inputSize > 0 ? (double) outputSize / inputSize : 1.0);
        
        return result;
    }
    
    /**
     * Execute with grouping support - enhanced version
     */
    public List<Map<String, Object>> executeWithAggregation(Pipeline pipeline) {
        List<Map<String, Object>> result = context.getTable(pipeline.getTableName());
        
        boolean hasGroupBy = false;
        GroupOp groupOp = null;
        SelectOp aggregateSelect = null;
        
        // First pass: identify group and aggregate operations
        for (int i = 0; i < pipeline.getOperations().size(); i++) {
            Operation op = pipeline.getOperations().get(i);
            if (op instanceof GroupOp) {
                hasGroupBy = true;
                groupOp = (GroupOp) op;
                // Check if next operation is select (with aggregates)
                if (i + 1 < pipeline.getOperations().size() && 
                    pipeline.getOperations().get(i + 1) instanceof SelectOp) {
                    aggregateSelect = (SelectOp) pipeline.getOperations().get(i + 1);
                }
            }
        }
        
        // Execute operations
        for (int i = 0; i < pipeline.getOperations().size(); i++) {
            Operation op = pipeline.getOperations().get(i);
            
            if (op instanceof GroupOp) {
                // Execute grouping
                result = op.execute(result, context);
            } else if (hasGroupBy && op == aggregateSelect) {
                // Execute aggregate select with group context
                result = executeAggregateSelect(aggregateSelect, result);
            } else if (!(hasGroupBy && op instanceof SelectOp && i > 0 && 
                       pipeline.getOperations().get(i - 1) instanceof GroupOp)) {
                // Execute normal operation (skip aggregate select as it's handled above)
                result = op.execute(result, context);
            }
        }
        
        return result;
    }
    
    private List<Map<String, Object>> executeAggregateSelect(SelectOp select, List<Map<String, Object>> groupedInput) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        // Get all groups
        Map<String, List<Map<String, Object>>> allGroups = new LinkedHashMap<>();
        
        // Rebuild groups from input (each row represents a group)
        for (Map<String, Object> groupRow : groupedInput) {
            String groupKey = buildGroupKey(groupRow, context);
            List<Map<String, Object>> group = context.getCurrentGroup();
            if (group != null) {
                allGroups.put(groupKey, new ArrayList<>(group));
            }
        }
        
        // Process each group
        for (Map.Entry<String, List<Map<String, Object>>> entry : allGroups.entrySet()) {
            List<Map<String, Object>> group = entry.getValue();
            context.setCurrentGroup(group);
            
            Map<String, Object> resultRow = new LinkedHashMap<>();
            
            // Evaluate each column
            for (ColumnSpec col : select.getColumns()) {
                if (col.isWildcard()) {
                    // Add all columns from first row of group
                    if (!group.isEmpty()) {
                        resultRow.putAll(group.get(0));
                    }
                } else if (col.getExpression() instanceof FunctionCall) {
                    // Aggregate function
                    FunctionCall func = (FunctionCall) col.getExpression();
                    Object value = evaluateAggregateFunction(func, group);
                    
                    String colName = col.getAlias() != null ? col.getAlias() : func.getName();
                    resultRow.put(colName, value);
                } else {
                    // Regular expression - evaluate with first row of group
                    if (!group.isEmpty()) {
                        Object value = col.getExpression().evaluate(group.get(0), context);
                        String colName = col.getAlias() != null ? col.getAlias() : 
                                        (col.getExpression() instanceof Variable ? 
                                         ((Variable) col.getExpression()).getName() : "col");
                        resultRow.put(colName, value);
                    }
                }
            }
            
            result.add(resultRow);
        }
        
        return result;
    }
    
    private Object evaluateAggregateFunction(FunctionCall func, List<Map<String, Object>> group) {
        String funcName = func.getName().toLowerCase();
        
        // Special handling for aggregate functions
        switch (funcName) {
            case "count":
                return (double) group.size();
                
            case "sum":
            case "avg":
            case "min":
            case "max": {
                // Get the column values from the group
                if (func.getArguments().isEmpty()) {
                    return null;
                }
                
                Expression arg = func.getArguments().get(0);
                List<Object> values = new ArrayList<>();
                for (Map<String, Object> row : group) {
                    Object val = arg.evaluate(row, context);
                    if (val != null) {
                        values.add(val);
                    }
                }
                
                if (values.isEmpty()) {
                    return null;
                }
                
                switch (funcName) {
                    case "sum": {
                        double sum = 0;
                        for (Object val : values) {
                            if (val instanceof Number) {
                                sum += ((Number) val).doubleValue();
                            }
                        }
                        return sum;
                    }
                    case "avg": {
                        double sum = 0;
                        for (Object val : values) {
                            if (val instanceof Number) {
                                sum += ((Number) val).doubleValue();
                            }
                        }
                        return sum / values.size();
                    }
                    case "min": {
                        double min = Double.MAX_VALUE;
                        for (Object val : values) {
                            if (val instanceof Number) {
                                min = Math.min(min, ((Number) val).doubleValue());
                            }
                        }
                        return min;
                    }
                    case "max": {
                        double max = Double.MIN_VALUE;
                        for (Object val : values) {
                            if (val instanceof Number) {
                                max = Math.max(max, ((Number) val).doubleValue());
                            }
                        }
                        return max;
                    }
                }
                break;
            }
            
            case "first":
                if (!group.isEmpty() && !func.getArguments().isEmpty()) {
                    return func.getArguments().get(0).evaluate(group.get(0), context);
                }
                return null;
                
            case "last":
                if (!group.isEmpty() && !func.getArguments().isEmpty()) {
                    return func.getArguments().get(0).evaluate(group.get(group.size() - 1), context);
                }
                return null;
        }
        
        // Fallback to regular function call
        List<Object> args = new ArrayList<>();
        for (Expression arg : func.getArguments()) {
            if (!group.isEmpty()) {
                args.add(arg.evaluate(group.get(0), context));
            }
        }
        return context.callFunction(funcName, args);
    }
    
    private String buildGroupKey(Map<String, Object> row, QueryContext context) {
        StringBuilder key = new StringBuilder();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (key.length() > 0) key.append("|");
            key.append(entry.getValue());
        }
        return key.toString();
    }
}
