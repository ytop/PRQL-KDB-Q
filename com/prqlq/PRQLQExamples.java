package com.prqlq;

import java.util.*;

/**
 * Examples and tests for PRQL-Q language
 */
public class PRQLQExamples {
    
    public static void main(String[] args) {
        PRQLQ engine = new PRQLQ();
        
        // Create sample data
        setupSampleData(engine);
        
        System.out.println("=" .repeat(80));
        System.out.println("PRQL-Q Language Examples");
        System.out.println("=" .repeat(80));
        System.out.println();
        
        // Run examples
        example1_SimpleFilter(engine);
        example2_SelectColumns(engine);
        example3_Sorting(engine);
        example4_Aggregation(engine);
        example5_ComplexFilter(engine);
        example6_Limit(engine);
        example7_MultipleOperations(engine);
        example8_ExplainPlan(engine);
    }
    
    private static void setupSampleData(PRQLQ engine) {
        // Employees table
        List<Map<String, Object>> employees = PRQLQ.table()
            .columns("id", "name", "dept", "salary", "age", "city")
            .row(1, "Alice", "sales", 75000.0, 32, "New York")
            .row(2, "Bob", "engineering", 95000.0, 28, "San Francisco")
            .row(3, "Charlie", "sales", 68000.0, 45, "Boston")
            .row(4, "Diana", "engineering", 105000.0, 35, "Seattle")
            .row(5, "Eve", "marketing", 62000.0, 29, "Chicago")
            .row(6, "Frank", "sales", 82000.0, 41, "New York")
            .row(7, "Grace", "engineering", 98000.0, 31, "San Francisco")
            .row(8, "Henry", "marketing", 59000.0, 26, "Austin")
            .row(9, "Iris", "sales", 71000.0, 38, "Boston")
            .row(10, "Jack", "engineering", 89000.0, 33, "Seattle")
            .build();
        
        engine.registerTable("employees", employees);
        
        // Sales table
        List<Map<String, Object>> sales = PRQLQ.table()
            .columns("id", "product", "amount", "quarter", "region")
            .row(1, "Widget A", 15000.0, 1, "East")
            .row(2, "Widget B", 22000.0, 1, "West")
            .row(3, "Widget A", 18000.0, 2, "East")
            .row(4, "Widget C", 31000.0, 2, "South")
            .row(5, "Widget B", 25000.0, 3, "West")
            .row(6, "Widget A", 19000.0, 3, "East")
            .row(7, "Widget C", 28000.0, 4, "South")
            .row(8, "Widget B", 23000.0, 4, "West")
            .build();
        
        engine.registerTable("sales", sales);
    }
    
    private static void example1_SimpleFilter(PRQLQ engine) {
        System.out.println("Example 1: Simple Filter");
        System.out.println("Query: employees | ?[salary > 80000]");
        System.out.println();
        
        String query = "employees | ?[salary > 80000]";
        List<Map<String, Object>> results = engine.query(query);
        
        System.out.println(PRQLQ.formatResults(results));
        System.out.println("Returned " + results.size() + " rows\n");
    }
    
    private static void example2_SelectColumns(PRQLQ engine) {
        System.out.println("Example 2: Select Specific Columns");
        System.out.println("Query: employees | ![name; dept; salary]");
        System.out.println();
        
        String query = "employees | ![name; dept; salary]";
        List<Map<String, Object>> results = engine.query(query);
        
        System.out.println(PRQLQ.formatResults(results));
        System.out.println("Returned " + results.size() + " rows\n");
    }
    
    private static void example3_Sorting(PRQLQ engine) {
        System.out.println("Example 3: Sorting");
        System.out.println("Query: employees | ^[salary] | #[5]");
        System.out.println("(Sort by salary ascending, take top 5)");
        System.out.println();
        
        String query = "employees | ^[salary] | #[5]";
        List<Map<String, Object>> results = engine.query(query);
        
        System.out.println(PRQLQ.formatResults(results));
        System.out.println("Returned " + results.size() + " rows\n");
    }
    
    private static void example4_Aggregation(PRQLQ engine) {
        System.out.println("Example 4: Aggregation by Department");
        System.out.println("Query: employees | @[dept] | ![dept; count:count[id]; avg_salary:avg[salary]]");
        System.out.println();
        
        String query = "employees | @[dept] | ![dept; count:count[id]; avg_salary:avg[salary]]";
        List<Map<String, Object>> results = engine.query(query);
        
        System.out.println(PRQLQ.formatResults(results));
        System.out.println("Returned " + results.size() + " rows\n");
    }
    
    private static void example5_ComplexFilter(PRQLQ engine) {
        System.out.println("Example 5: Complex Filter with AND/OR");
        System.out.println("Query: employees | ?[(dept = `sales & salary > 70000) | age > 40]");
        System.out.println();
        
        String query = "employees | ?[(dept = `sales & salary > 70000) | age > 40]";
        List<Map<String, Object>> results = engine.query(query);
        
        System.out.println(PRQLQ.formatResults(results));
        System.out.println("Returned " + results.size() + " rows\n");
    }
    
    private static void example6_Limit(PRQLQ engine) {
        System.out.println("Example 6: Limit and Drop");
        System.out.println("Query: employees | v[salary] | _[3] | #[3]");
        System.out.println("(Sort descending, skip 3, take 3)");
        System.out.println();
        
        String query = "employees | v[salary] | _[3] | #[3]";
        List<Map<String, Object>> results = engine.query(query);
        
        System.out.println(PRQLQ.formatResults(results));
        System.out.println("Returned " + results.size() + " rows\n");
    }
    
    private static void example7_MultipleOperations(PRQLQ engine) {
        System.out.println("Example 7: Complex Pipeline");
        System.out.println("Query: employees | ?[salary > 60000] | ![name; dept; salary; bonus:salary * 0.1] | v[bonus] | #[5]");
        System.out.println();
        
        String query = "employees | ?[salary > 60000] | ![name; dept; salary; bonus:salary * 0.1] | v[bonus] | #[5]";
        List<Map<String, Object>> results = engine.query(query);
        
        System.out.println(PRQLQ.formatResults(results));
        System.out.println("Returned " + results.size() + " rows\n");
    }
    
    private static void example8_ExplainPlan(PRQLQ engine) {
        System.out.println("Example 8: Execution Plan");
        System.out.println("Query: employees | ?[age > 30] | ?[salary > 70000] | ![name; salary] | ^[salary]");
        System.out.println();
        
        String query = "employees | ?[age > 30] | ?[salary > 70000] | ![name; salary] | ^[salary]";
        QueryPlanner.ExecutionPlan plan = engine.explain(query);
        
        System.out.println(plan);
        
        System.out.println("\nExecuting optimized plan:");
        List<Map<String, Object>> results = engine.query(query, true);
        System.out.println(PRQLQ.formatResults(results));
        System.out.println("Returned " + results.size() + " rows\n");
    }
    
    /**
     * Additional test cases
     */
    public static void runAdditionalTests() {
        PRQLQ engine = new PRQLQ();
        setupSampleData(engine);
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("Additional Test Cases");
        System.out.println("=".repeat(80) + "\n");
        
        // Test 1: All employees in engineering
        System.out.println("Test 1: Engineering department employees");
        String q1 = "employees | ?[dept = `engineering] | ![name; salary]";
        System.out.println("Query: " + q1);
        System.out.println(PRQLQ.formatResults(engine.query(q1)));
        
        // Test 2: Average salary by city
        System.out.println("\nTest 2: Average salary by city");
        String q2 = "employees | @[city] | ![city; avg_sal:avg[salary]; count:count[id]]";
        System.out.println("Query: " + q2);
        System.out.println(PRQLQ.formatResults(engine.query(q2)));
        
        // Test 3: Top 3 highest paid employees
        System.out.println("\nTest 3: Top 3 highest paid employees");
        String q3 = "employees | v[salary] | #[3] | ![name; salary; dept]";
        System.out.println("Query: " + q3);
        System.out.println(PRQLQ.formatResults(engine.query(q3)));
        
        // Test 4: Employees with computed columns
        System.out.println("\nTest 4: Computed columns");
        String q4 = "employees | ![name; salary; annual:salary * 12; monthly:salary] | #[5]";
        System.out.println("Query: " + q4);
        System.out.println(PRQLQ.formatResults(engine.query(q4)));
        
        // Test 5: Sales aggregation
        System.out.println("\nTest 5: Sales by product");
        String q5 = "sales | @[product] | ![product; total:sum[amount]; avg_amount:avg[amount]]";
        System.out.println("Query: " + q5);
        System.out.println(PRQLQ.formatResults(engine.query(q5)));
        
        // Test 6: Filtered aggregation
        System.out.println("\nTest 6: Departments with average salary > 70000");
        String q6 = "employees | @[dept] | ![dept; avg_sal:avg[salary]; cnt:count[id]] | ?[avg_sal > 70000]";
        System.out.println("Query: " + q6);
        System.out.println(PRQLQ.formatResults(engine.query(q6)));
        
        // Test 7: Multiple filters
        System.out.println("\nTest 7: Multiple filter conditions");
        String q7 = "employees | ?[age >= 30] | ?[age <= 40] | ?[salary > 70000] | ![name; age; salary]";
        System.out.println("Query: " + q7);
        System.out.println(PRQLQ.formatResults(engine.query(q7)));
    }
}
