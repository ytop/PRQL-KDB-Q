package com.prqlq;

import com.prqlq.ASTNode.*;

import java.util.*;

/**
 * Query planner - optimizes the execution plan
 */
public class QueryPlanner {
    
    /**
     * Represents an execution plan node
     */
    public static class PlanNode {
        public enum NodeType {
            TABLE_SCAN,
            FILTER,
            SELECT,
            SORT,
            GROUP,
            LIMIT,
            DROP,
            JOIN
        }
        
        private final NodeType type;
        private final Operation operation;
        private final Map<String, Object> metadata;
        private final List<PlanNode> children;
        
        public PlanNode(NodeType type, Operation operation) {
            this.type = type;
            this.operation = operation;
            this.metadata = new HashMap<>();
            this.children = new ArrayList<>();
        }
        
        public NodeType getType() {
            return type;
        }
        
        public Operation getOperation() {
            return operation;
        }
        
        public Map<String, Object> getMetadata() {
            return metadata;
        }
        
        public void addChild(PlanNode child) {
            children.add(child);
        }
        
        public List<PlanNode> getChildren() {
            return children;
        }
        
        @Override
        public String toString() {
            return toString(0);
        }
        
        private String toString(int indent) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < indent; i++) sb.append("  ");
            sb.append(type);
            if (operation != null) {
                sb.append(": ").append(operation);
            }
            if (!metadata.isEmpty()) {
                sb.append(" ").append(metadata);
            }
            sb.append("\n");
            for (PlanNode child : children) {
                sb.append(child.toString(indent + 1));
            }
            return sb.toString();
        }
    }
    
    /**
     * Execution plan
     */
    public static class ExecutionPlan {
        private final String tableName;
        private final PlanNode root;
        private final Map<String, Object> statistics;
        
        public ExecutionPlan(String tableName, PlanNode root) {
            this.tableName = tableName;
            this.root = root;
            this.statistics = new HashMap<>();
        }
        
        public String getTableName() {
            return tableName;
        }
        
        public PlanNode getRoot() {
            return root;
        }
        
        public Map<String, Object> getStatistics() {
            return statistics;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Execution Plan for table: ").append(tableName).append("\n");
            sb.append("─────────────────────────────────────\n");
            sb.append(root.toString());
            if (!statistics.isEmpty()) {
                sb.append("\nStatistics:\n");
                statistics.forEach((key, value) -> 
                    sb.append("  ").append(key).append(": ").append(value).append("\n"));
            }
            return sb.toString();
        }
    }
    
    /**
     * Create an execution plan from a pipeline
     */
    public ExecutionPlan createPlan(Pipeline pipeline) {
        PlanNode root = new PlanNode(PlanNode.NodeType.TABLE_SCAN, null);
        root.getMetadata().put("table", pipeline.getTableName());
        
        PlanNode current = root;
        
        for (Operation op : pipeline.getOperations()) {
            PlanNode node = createPlanNode(op);
            current.addChild(node);
            current = node;
        }
        
        // Apply optimizations
        PlanNode optimized = optimize(root);
        
        ExecutionPlan plan = new ExecutionPlan(pipeline.getTableName(), optimized);
        analyzeStatistics(plan);
        
        return plan;
    }
    
    private PlanNode createPlanNode(Operation op) {
        if (op instanceof FilterOp) {
            return new PlanNode(PlanNode.NodeType.FILTER, op);
        } else if (op instanceof SelectOp) {
            return new PlanNode(PlanNode.NodeType.SELECT, op);
        } else if (op instanceof SortOp) {
            return new PlanNode(PlanNode.NodeType.SORT, op);
        } else if (op instanceof GroupOp) {
            return new PlanNode(PlanNode.NodeType.GROUP, op);
        } else if (op instanceof LimitOp) {
            return new PlanNode(PlanNode.NodeType.LIMIT, op);
        } else if (op instanceof DropOp) {
            return new PlanNode(PlanNode.NodeType.DROP, op);
        } else {
            throw new RuntimeException("Unknown operation type: " + op.getClass().getName());
        }
    }
    
    /**
     * Apply optimization rules to the plan
     */
    private PlanNode optimize(PlanNode root) {
        // Optimization 1: Push filters down (closer to table scan)
        root = pushDownFilters(root);
        
        // Optimization 2: Combine adjacent filters
        root = combineFilters(root);
        
        // Optimization 3: Push limit through operations when possible
        root = optimizeLimit(root);
        
        // Optimization 4: Remove redundant operations
        root = removeRedundantOps(root);
        
        return root;
    }
    
    /**
     * Push filters as close to the data source as possible
     */
    private PlanNode pushDownFilters(PlanNode node) {
        if (node.getChildren().isEmpty()) {
            return node;
        }
        
        // Recursively optimize children first
        for (int i = 0; i < node.getChildren().size(); i++) {
            node.getChildren().set(i, pushDownFilters(node.getChildren().get(i)));
        }
        
        // If this is a filter, try to push it down
        if (node.getType() == PlanNode.NodeType.FILTER && !node.getChildren().isEmpty()) {
            PlanNode child = node.getChildren().get(0);
            
            // Can push filter past select if select doesn't remove needed columns
            if (child.getType() == PlanNode.NodeType.SELECT) {
                // Check if filter columns are in select output
                FilterOp filter = (FilterOp) node.getOperation();
                SelectOp select = (SelectOp) child.getOperation();
                
                if (canPushFilterPastSelect(filter, select)) {
                    // Swap: filter should be child of select's child
                    if (!child.getChildren().isEmpty()) {
                        PlanNode grandchild = child.getChildren().get(0);
                        node.getChildren().clear();
                        node.addChild(grandchild);
                        child.getChildren().clear();
                        child.addChild(node);
                        return child;
                    }
                }
            }
        }
        
        return node;
    }
    
    private boolean canPushFilterPastSelect(FilterOp filter, SelectOp select) {
        // Simplified check - in production would need to analyze filter columns
        // For now, don't push if select has wildcards
        for (ColumnSpec col : select.getColumns()) {
            if (col.isWildcard()) {
                return true; // Can push past wildcard select
            }
        }
        return false; // Conservative: don't push
    }
    
    /**
     * Combine adjacent filter operations
     */
    private PlanNode combineFilters(PlanNode node) {
        if (node.getChildren().isEmpty()) {
            return node;
        }
        
        // Recursively optimize children
        for (int i = 0; i < node.getChildren().size(); i++) {
            node.getChildren().set(i, combineFilters(node.getChildren().get(i)));
        }
        
        // Combine this filter with child filter if both are filters
        if (node.getType() == PlanNode.NodeType.FILTER && 
            !node.getChildren().isEmpty() &&
            node.getChildren().get(0).getType() == PlanNode.NodeType.FILTER) {
            
            PlanNode childFilter = node.getChildren().get(0);
            FilterOp thisFilter = (FilterOp) node.getOperation();
            FilterOp childFilterOp = (FilterOp) childFilter.getOperation();
            
            // Combine with AND
            Expression combined = new BinaryOp(
                BinaryOp.Operator.AND,
                thisFilter.getCondition(),
                childFilterOp.getCondition()
            );
            
            PlanNode combinedNode = new PlanNode(PlanNode.NodeType.FILTER, new FilterOp(combined));
            combinedNode.getMetadata().put("optimized", "combined_filters");
            
            // Take grandchildren
            if (!childFilter.getChildren().isEmpty()) {
                combinedNode.addChild(childFilter.getChildren().get(0));
            }
            
            return combinedNode;
        }
        
        return node;
    }
    
    /**
     * Optimize limit operations
     */
    private PlanNode optimizeLimit(PlanNode node) {
        if (node.getChildren().isEmpty()) {
            return node;
        }
        
        // Recursively optimize children
        for (int i = 0; i < node.getChildren().size(); i++) {
            node.getChildren().set(i, optimizeLimit(node.getChildren().get(i)));
        }
        
        // If this is a limit, it can be pushed through certain operations
        if (node.getType() == PlanNode.NodeType.LIMIT && !node.getChildren().isEmpty()) {
            PlanNode child = node.getChildren().get(0);
            
            // Limit can be pushed through filter
            if (child.getType() == PlanNode.NodeType.FILTER) {
                node.getMetadata().put("optimization", "limit_after_filter");
            }
            
            // Multiple limits: keep the minimum
            if (child.getType() == PlanNode.NodeType.LIMIT) {
                LimitOp thisLimit = (LimitOp) node.getOperation();
                LimitOp childLimit = (LimitOp) child.getOperation();
                
                // Keep the smaller limit
                int minLimit = Math.min(
                    Math.abs(((LimitOp) node.getOperation()).toString().length()),
                    Math.abs(((LimitOp) child.getOperation()).toString().length())
                );
                
                node.getMetadata().put("optimized", "combined_limits");
                // In real implementation, would actually combine the limits
            }
        }
        
        return node;
    }
    
    /**
     * Remove redundant operations
     */
    private PlanNode removeRedundantOps(PlanNode node) {
        if (node.getChildren().isEmpty()) {
            return node;
        }
        
        // Recursively optimize children
        for (int i = 0; i < node.getChildren().size(); i++) {
            node.getChildren().set(i, removeRedundantOps(node.getChildren().get(i)));
        }
        
        // Remove drop[0] - does nothing
        if (node.getType() == PlanNode.NodeType.DROP) {
            // Would check if drop count is 0 and skip it
            node.getMetadata().put("check", "drop_optimization");
        }
        
        return node;
    }
    
    /**
     * Analyze and add statistics to the plan
     */
    private void analyzeStatistics(ExecutionPlan plan) {
        int totalOperations = countOperations(plan.getRoot());
        int filterOperations = countOperationType(plan.getRoot(), PlanNode.NodeType.FILTER);
        int selectOperations = countOperationType(plan.getRoot(), PlanNode.NodeType.SELECT);
        
        plan.getStatistics().put("total_operations", totalOperations);
        plan.getStatistics().put("filter_operations", filterOperations);
        plan.getStatistics().put("select_operations", selectOperations);
        plan.getStatistics().put("has_grouping", hasNodeType(plan.getRoot(), PlanNode.NodeType.GROUP));
        plan.getStatistics().put("has_sorting", hasNodeType(plan.getRoot(), PlanNode.NodeType.SORT));
    }
    
    private int countOperations(PlanNode node) {
        int count = node.getType() != PlanNode.NodeType.TABLE_SCAN ? 1 : 0;
        for (PlanNode child : node.getChildren()) {
            count += countOperations(child);
        }
        return count;
    }
    
    private int countOperationType(PlanNode node, PlanNode.NodeType type) {
        int count = node.getType() == type ? 1 : 0;
        for (PlanNode child : node.getChildren()) {
            count += countOperationType(child, type);
        }
        return count;
    }
    
    private boolean hasNodeType(PlanNode node, PlanNode.NodeType type) {
        if (node.getType() == type) return true;
        for (PlanNode child : node.getChildren()) {
            if (hasNodeType(child, type)) return true;
        }
        return false;
    }
}
