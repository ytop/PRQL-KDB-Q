package com.prqlq;

import com.prqlq.ASTNode.*;
import com.prqlq.Lexer.*;
import com.prqlq.QueryPlanner.*;

import java.util.*;

/**
 * Main PRQL-Q query engine
 * Provides the public API for executing PRQL-Q queries
 */
public class PRQLQ {
    
    private final QueryContext context;
    private final QueryExecutor executor;
    private final QueryPlanner planner;
    
    public PRQLQ() {
        this.context = new QueryContext();
        this.executor = new QueryExecutor(context);
        this.planner = new QueryPlanner();
    }
    
    /**
     * Register a table (List<Map<String, Object>>) with the query engine
     */
    public void registerTable(String name, List<Map<String, Object>> data) {
        context.registerTable(name, data);
    }
    
    /**
     * Execute a PRQL-Q query and return results
     */
    public List<Map<String, Object>> query(String query) {
        return query(query, false);
    }
    
    /**
     * Execute a PRQL-Q query with optional optimization
     */
    public List<Map<String, Object>> query(String query, boolean optimize) {
        // Tokenize
        Lexer lexer = new Lexer(query);
        List<Token> tokens = lexer.tokenize();
        
        // Parse
        Parser parser = new Parser(tokens);
        Pipeline pipeline = parser.parse();
        
        if (optimize) {
            // Create and execute optimized plan
            ExecutionPlan plan = planner.createPlan(pipeline);
            return executor.execute(plan);
        } else {
            // Execute directly
            return executor.executeWithAggregation(pipeline);
        }
    }
    
    /**
     * Get the execution plan for a query (for debugging)
     */
    public ExecutionPlan explain(String query) {
        Lexer lexer = new Lexer(query);
        List<Token> tokens = lexer.tokenize();
        
        Parser parser = new Parser(tokens);
        Pipeline pipeline = parser.parse();
        
        return planner.createPlan(pipeline);
    }
    
    /**
     * Pretty print query results
     */
    public static String formatResults(List<Map<String, Object>> results) {
        if (results.isEmpty()) {
            return "No results";
        }
        
        // Get all column names
        Set<String> allColumns = new LinkedHashSet<>();
        for (Map<String, Object> row : results) {
            allColumns.addAll(row.keySet());
        }
        
        List<String> columns = new ArrayList<>(allColumns);
        
        // Calculate column widths
        Map<String, Integer> columnWidths = new HashMap<>();
        for (String col : columns) {
            columnWidths.put(col, col.length());
        }
        
        for (Map<String, Object> row : results) {
            for (String col : columns) {
                Object value = row.get(col);
                String valueStr = value == null ? "null" : value.toString();
                columnWidths.put(col, Math.max(columnWidths.get(col), valueStr.length()));
            }
        }
        
        // Build output
        StringBuilder sb = new StringBuilder();
        
        // Header
        for (String col : columns) {
            sb.append("| ").append(pad(col, columnWidths.get(col))).append(" ");
        }
        sb.append("|\n");
        
        // Separator
        for (String col : columns) {
            sb.append("|-").append(repeat("-", columnWidths.get(col))).append("-");
        }
        sb.append("|\n");
        
        // Rows
        for (Map<String, Object> row : results) {
            for (String col : columns) {
                Object value = row.get(col);
                String valueStr = value == null ? "null" : formatValue(value);
                sb.append("| ").append(pad(valueStr, columnWidths.get(col))).append(" ");
            }
            sb.append("|\n");
        }
        
        return sb.toString();
    }
    
    private static String formatValue(Object value) {
        if (value instanceof Double) {
            double d = (Double) value;
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.format("%.0f", d);
            } else {
                return String.format("%.2f", d);
            }
        }
        return value.toString();
    }
    
    private static String pad(String str, int length) {
        if (str.length() >= length) {
            return str;
        }
        StringBuilder sb = new StringBuilder(str);
        while (sb.length() < length) {
            sb.append(" ");
        }
        return sb.toString();
    }
    
    private static String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
    
    /**
     * Convert a simple table representation to List<Map<String, Object>>
     */
    public static List<Map<String, Object>> createTable(String[] columns, Object[][] rows) {
        List<Map<String, Object>> table = new ArrayList<>();
        
        for (Object[] row : rows) {
            Map<String, Object> rowMap = new LinkedHashMap<>();
            for (int i = 0; i < columns.length && i < row.length; i++) {
                rowMap.put(columns[i], row[i]);
            }
            table.add(rowMap);
        }
        
        return table;
    }
    
    /**
     * Builder for creating test data
     */
    public static class TableBuilder {
        private final List<String> columns = new ArrayList<>();
        private final List<Map<String, Object>> rows = new ArrayList<>();
        
        public TableBuilder columns(String... cols) {
            columns.addAll(Arrays.asList(cols));
            return this;
        }
        
        public TableBuilder row(Object... values) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < columns.size() && i < values.length; i++) {
                row.put(columns.get(i), values[i]);
            }
            rows.add(row);
            return this;
        }
        
        public List<Map<String, Object>> build() {
            return rows;
        }
    }
    
    public static TableBuilder table() {
        return new TableBuilder();
    }
}
