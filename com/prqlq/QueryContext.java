package com.prqlq;

import java.util.*;
import java.util.function.Function;

/**
 * Query execution context
 * Manages state, variables, and functions during query execution
 */
public class QueryContext {
    
    private Map<String, List<Map<String, Object>>> tables;
    private Map<String, List<Map<String, Object>>> groups;
    private List<String> groupByColumns;
    private List<Map<String, Object>> currentGroup;
    private Map<String, Function<List<Object>, Object>> functions;
    
    public QueryContext() {
        this.tables = new HashMap<>();
        this.functions = new HashMap<>();
        initializeBuiltInFunctions();
    }
    
    public void registerTable(String name, List<Map<String, Object>> data) {
        tables.put(name, data);
    }
    
    public List<Map<String, Object>> getTable(String name) {
        List<Map<String, Object>> table = tables.get(name);
        if (table == null) {
            throw new RuntimeException("Table not found: " + name);
        }
        return table;
    }
    
    public void setGroups(Map<String, List<Map<String, Object>>> groups) {
        this.groups = groups;
    }
    
    public void setGroupByColumns(List<String> columns) {
        this.groupByColumns = columns;
    }
    
    public void setCurrentGroup(List<Map<String, Object>> group) {
        this.currentGroup = group;
    }
    
    public List<Map<String, Object>> getCurrentGroup() {
        return currentGroup;
    }
    
    public boolean isGrouped() {
        return currentGroup != null;
    }
    
    public void registerFunction(String name, Function<List<Object>, Object> func) {
        functions.put(name.toLowerCase(), func);
    }
    
    public Object callFunction(String name, List<Object> args) {
        Function<List<Object>, Object> func = functions.get(name.toLowerCase());
        if (func == null) {
            throw new RuntimeException("Unknown function: " + name);
        }
        return func.apply(args);
    }
    
    private void initializeBuiltInFunctions() {
        // Aggregate functions - work on current group if in group context
        
        // count - count rows
        registerFunction("count", args -> {
            if (currentGroup != null) {
                return (double) currentGroup.size();
            }
            return 1.0;
        });
        
        // sum - sum values
        registerFunction("sum", args -> {
            if (args.isEmpty()) return 0.0;
            
            if (currentGroup != null) {
                // In group context, need to sum over the group
                // This is a simplified version - in real implementation
                // we'd need to pass the column name and evaluate it for each row
                double sum = 0;
                for (Object val : args) {
                    if (val instanceof Number) {
                        sum += ((Number) val).doubleValue();
                    }
                }
                return sum;
            }
            
            double sum = 0;
            for (Object arg : args) {
                if (arg instanceof Number) {
                    sum += ((Number) arg).doubleValue();
                }
            }
            return sum;
        });
        
        // avg - average values
        registerFunction("avg", args -> {
            if (args.isEmpty()) return null;
            
            double sum = 0;
            int count = 0;
            for (Object arg : args) {
                if (arg instanceof Number) {
                    sum += ((Number) arg).doubleValue();
                    count++;
                }
            }
            return count > 0 ? sum / count : null;
        });
        
        // min - minimum value
        registerFunction("min", args -> {
            if (args.isEmpty()) return null;
            
            Double min = null;
            for (Object arg : args) {
                if (arg instanceof Number) {
                    double val = ((Number) arg).doubleValue();
                    if (min == null || val < min) {
                        min = val;
                    }
                }
            }
            return min;
        });
        
        // max - maximum value
        registerFunction("max", args -> {
            if (args.isEmpty()) return null;
            
            Double max = null;
            for (Object arg : args) {
                if (arg instanceof Number) {
                    double val = ((Number) arg).doubleValue();
                    if (max == null || val > max) {
                        max = val;
                    }
                }
            }
            return max;
        });
        
        // first - first value
        registerFunction("first", args -> {
            return args.isEmpty() ? null : args.get(0);
        });
        
        // last - last value
        registerFunction("last", args -> {
            return args.isEmpty() ? null : args.get(args.size() - 1);
        });
        
        // String functions
        
        // upper - uppercase
        registerFunction("upper", args -> {
            if (args.isEmpty() || args.get(0) == null) return null;
            return args.get(0).toString().toUpperCase();
        });
        
        // lower - lowercase
        registerFunction("lower", args -> {
            if (args.isEmpty() || args.get(0) == null) return null;
            return args.get(0).toString().toLowerCase();
        });
        
        // length - string length
        registerFunction("length", args -> {
            if (args.isEmpty() || args.get(0) == null) return 0.0;
            return (double) args.get(0).toString().length();
        });
        
        // substring - extract substring
        registerFunction("substring", args -> {
            if (args.size() < 2 || args.get(0) == null) return null;
            String str = args.get(0).toString();
            int start = ((Number) args.get(1)).intValue();
            if (args.size() >= 3) {
                int length = ((Number) args.get(2)).intValue();
                return str.substring(start, Math.min(start + length, str.length()));
            }
            return str.substring(start);
        });
        
        // concat - concatenate strings
        registerFunction("concat", args -> {
            StringBuilder sb = new StringBuilder();
            for (Object arg : args) {
                if (arg != null) {
                    sb.append(arg.toString());
                }
            }
            return sb.toString();
        });
        
        // Math functions
        
        // abs - absolute value
        registerFunction("abs", args -> {
            if (args.isEmpty() || args.get(0) == null) return null;
            return Math.abs(((Number) args.get(0)).doubleValue());
        });
        
        // round - round to nearest integer
        registerFunction("round", args -> {
            if (args.isEmpty() || args.get(0) == null) return null;
            double value = ((Number) args.get(0)).doubleValue();
            if (args.size() >= 2) {
                int decimals = ((Number) args.get(1)).intValue();
                double factor = Math.pow(10, decimals);
                return Math.round(value * factor) / factor;
            }
            return (double) Math.round(value);
        });
        
        // floor - round down
        registerFunction("floor", args -> {
            if (args.isEmpty() || args.get(0) == null) return null;
            return Math.floor(((Number) args.get(0)).doubleValue());
        });
        
        // ceil - round up
        registerFunction("ceil", args -> {
            if (args.isEmpty() || args.get(0) == null) return null;
            return Math.ceil(((Number) args.get(0)).doubleValue());
        });
        
        // sqrt - square root
        registerFunction("sqrt", args -> {
            if (args.isEmpty() || args.get(0) == null) return null;
            return Math.sqrt(((Number) args.get(0)).doubleValue());
        });
        
        // pow - power
        registerFunction("pow", args -> {
            if (args.size() < 2) return null;
            double base = ((Number) args.get(0)).doubleValue();
            double exponent = ((Number) args.get(1)).doubleValue();
            return Math.pow(base, exponent);
        });
        
        // Conditional functions
        
        // if - conditional
        registerFunction("if", args -> {
            if (args.size() < 3) return null;
            Object condition = args.get(0);
            boolean isTrue = false;
            
            if (condition instanceof Boolean) {
                isTrue = (Boolean) condition;
            } else if (condition instanceof Number) {
                isTrue = ((Number) condition).doubleValue() != 0;
            }
            
            return isTrue ? args.get(1) : args.get(2);
        });
        
        // coalesce - return first non-null value
        registerFunction("coalesce", args -> {
            for (Object arg : args) {
                if (arg != null) {
                    return arg;
                }
            }
            return null;
        });
        
        // Type conversion functions
        
        // tostring - convert to string
        registerFunction("tostring", args -> {
            if (args.isEmpty() || args.get(0) == null) return null;
            return args.get(0).toString();
        });
        
        // tonumber - convert to number
        registerFunction("tonumber", args -> {
            if (args.isEmpty() || args.get(0) == null) return null;
            try {
                return Double.parseDouble(args.get(0).toString());
            } catch (NumberFormatException e) {
                return null;
            }
        });
    }
    
    /**
     * Helper for aggregate functions to get column values from current group
     */
    public List<Object> getGroupColumnValues(String columnName) {
        if (currentGroup == null) {
            return Collections.emptyList();
        }
        
        List<Object> values = new ArrayList<>();
        for (Map<String, Object> row : currentGroup) {
            values.add(row.get(columnName));
        }
        return values;
    }
}
