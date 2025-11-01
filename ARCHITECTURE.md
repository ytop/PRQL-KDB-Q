# PRQL-Q Architecture Diagram

## System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         PRQL-Q Query Engine                         │
│                          (Java 8 Based)                             │
└─────────────────────────────────────────────────────────────────────┘

                    ┌──────────────────────┐
                    │   User Application   │
                    │  (Spring, REST, etc) │
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │    PRQLQ.java        │
                    │   (Main API)         │
                    │                      │
                    │ - registerTable()    │
                    │ - query()            │
                    │ - explain()          │
                    └──────────┬───────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
        ▼                      ▼                      ▼
┌──────────────┐    ┌──────────────────┐    ┌──────────────┐
│   Lexer      │───▶│     Parser       │───▶│   Planner    │
│              │    │                  │    │              │
│ Tokenization │    │ AST Construction │    │ Optimization │
└──────────────┘    └──────────────────┘    └──────┬───────┘
                                                    │
                                                    ▼
                                            ┌──────────────┐
                                            │   Executor   │
                                            │              │
                                            │ Execution    │
                                            └──────┬───────┘
                                                   │
                    ┌──────────────────────────────┼─────────────────┐
                    │                              │                 │
                    ▼                              ▼                 ▼
            ┌──────────────┐            ┌──────────────┐   ┌──────────────┐
            │ QueryContext │            │   ASTNode    │   │ Data Tables  │
            │              │            │              │   │              │
            │ - Functions  │            │ - Operations │   │ List<Map>    │
            │ - State      │            │ - Expressions│   └──────────────┘
            └──────────────┘            └──────────────┘

```

## Data Flow

```
Input Query String
      │
      ▼
┌──────────────────────────────────────────────────────────┐
│  "employees | ?[salary > 80000] | ![name; dept]"         │
└──────────────────────────────────────────────────────────┘
      │
      ▼ Lexer.tokenize()
┌──────────────────────────────────────────────────────────┐
│  [IDENTIFIER(employees), PIPE, FILTER, LBRACKET, ...]   │
└──────────────────────────────────────────────────────────┘
      │
      ▼ Parser.parse()
┌──────────────────────────────────────────────────────────┐
│  Pipeline(                                               │
│    tableName: "employees",                               │
│    operations: [                                         │
│      FilterOp(BinaryOp(GT, salary, 80000)),            │
│      SelectOp([ColumnSpec(name), ColumnSpec(dept)])     │
│    ]                                                     │
│  )                                                       │
└──────────────────────────────────────────────────────────┘
      │
      ▼ QueryPlanner.createPlan()
┌──────────────────────────────────────────────────────────┐
│  ExecutionPlan(                                          │
│    root: PlanNode(TABLE_SCAN)                           │
│      └─ PlanNode(FILTER) [optimized: combined_filters]  │
│           └─ PlanNode(SELECT)                           │
│  )                                                       │
└──────────────────────────────────────────────────────────┘
      │
      ▼ QueryExecutor.execute()
┌──────────────────────────────────────────────────────────┐
│ Step 1: Load table from context                         │
│   employees: [{id:1, name:"Alice", dept:"sales", ...}]  │
│                                                          │
│ Step 2: Execute filter                                  │
│   ?[salary > 80000]                                     │
│   → [{id:2, name:"Bob", ...}, {id:4, name:"Diana"...}] │
│                                                          │
│ Step 3: Execute select                                  │
│   ![name; dept]                                         │
│   → [{name:"Bob", dept:"engineering"}, ...]            │
└──────────────────────────────────────────────────────────┘
      │
      ▼
┌──────────────────────────────────────────────────────────┐
│  Results: List<Map<String, Object>>                     │
│  [                                                       │
│    {name: "Bob", dept: "engineering"},                  │
│    {name: "Diana", dept: "engineering"}                 │
│  ]                                                       │
└──────────────────────────────────────────────────────────┘

```

## Component Interaction

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Lexer (Lexer.java)                          │
├─────────────────────────────────────────────────────────────────────┤
│ Input:  String query                                                │
│ Output: List<Token>                                                 │
│                                                                      │
│ Responsibilities:                                                    │
│  • Character-by-character scanning                                  │
│  • Token recognition (operators, identifiers, literals)             │
│  • Comment handling                                                 │
│  • Error detection (line/column tracking)                           │
└─────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        Parser (Parser.java)                         │
├─────────────────────────────────────────────────────────────────────┤
│ Input:  List<Token>                                                 │
│ Output: Pipeline (AST)                                              │
│                                                                      │
│ Responsibilities:                                                    │
│  • Token stream consumption                                         │
│  • Syntax validation                                                │
│  • AST construction                                                 │
│  • Operator precedence handling                                     │
│  • Expression parsing                                               │
└─────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      ASTNode (ASTNode.java)                         │
├─────────────────────────────────────────────────────────────────────┤
│ Structures: Expression, Operation, Pipeline                         │
│                                                                      │
│ Expression Types:                                                    │
│  • Literal (numbers, strings, symbols)                              │
│  • Variable (column references)                                     │
│  • BinaryOp (arithmetic, comparison, logical)                       │
│  • UnaryOp (negation, NOT)                                          │
│  • FunctionCall (built-in functions)                                │
│                                                                      │
│ Operation Types:                                                     │
│  • FilterOp  - Row filtering                                        │
│  • SelectOp  - Column selection                                     │
│  • SortOp    - Ordering                                             │
│  • GroupOp   - Grouping                                             │
│  • LimitOp   - Row limiting                                         │
│  • DropOp    - Row skipping                                         │
└─────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│                  QueryPlanner (QueryPlanner.java)                   │
├─────────────────────────────────────────────────────────────────────┤
│ Input:  Pipeline                                                    │
│ Output: ExecutionPlan                                               │
│                                                                      │
│ Optimization Strategies:                                            │
│  1. Filter Pushdown                                                 │
│     • Move filters closer to table scan                             │
│     • Reduce data size early                                        │
│                                                                      │
│  2. Filter Combination                                              │
│     • Merge adjacent filters with AND                               │
│     • Single pass instead of multiple                               │
│                                                                      │
│  3. Limit Optimization                                              │
│     • Early termination                                             │
│     • Combine multiple limits                                       │
│                                                                      │
│  4. Redundancy Removal                                              │
│     • Remove no-op operations                                       │
│     • Simplify expressions                                          │
│                                                                      │
│ Statistics Collection:                                              │
│  • Operation counts                                                 │
│  • Complexity analysis                                              │
└─────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│                 QueryExecutor (QueryExecutor.java)                  │
├─────────────────────────────────────────────────────────────────────┤
│ Input:  ExecutionPlan, QueryContext                                │
│ Output: List<Map<String, Object>>                                  │
│                                                                      │
│ Execution Flow:                                                     │
│  1. Load table from context                                         │
│  2. Execute each operation in sequence                              │
│  3. Handle special cases (grouping, aggregation)                    │
│  4. Collect execution metrics                                       │
│                                                                      │
│ Performance Tracking:                                               │
│  • Input/output row counts                                          │
│  • Execution time per operation                                     │
│  • Selectivity ratios                                               │
└─────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│                QueryContext (QueryContext.java)                     │
├─────────────────────────────────────────────────────────────────────┤
│ Manages:                                                            │
│  • Registered tables                                                │
│  • Built-in functions                                               │
│  • Grouping state                                                   │
│  • Execution context                                                │
│                                                                      │
│ Built-in Functions:                                                 │
│  Aggregates: sum, avg, count, min, max, first, last                │
│  String: upper, lower, length, substring, concat                    │
│  Math: abs, round, floor, ceil, sqrt, pow                           │
│  Conditional: if, coalesce                                          │
│  Type: tostring, tonumber                                           │
└─────────────────────────────────────────────────────────────────────┘

```

## Operation Execution Flow

```
┌────────────────────────────────────────────────────────────────┐
│                      Filter Operation                          │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  Input: [{id:1, salary:75000}, {id:2, salary:95000}, ...]    │
│  Filter: ?[salary > 80000]                                    │
│                                                                │
│  For each row:                                                 │
│    1. Evaluate condition expression                            │
│    2. If true, include in result                               │
│    3. If false, exclude                                        │
│                                                                │
│  Output: [{id:2, salary:95000}, {id:4, salary:105000}]       │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│                      Select Operation                          │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  Input: [{id:1, name:"Alice", dept:"sales", salary:75000}]   │
│  Select: ![name; dept; bonus:salary * 0.1]                   │
│                                                                │
│  For each row:                                                 │
│    1. Create new row                                           │
│    2. For each column spec:                                    │
│       - If wildcard (*), copy all columns                      │
│       - If alias specified, use alias name                     │
│       - Evaluate expression and add to new row                 │
│                                                                │
│  Output: [{name:"Alice", dept:"sales", bonus:7500}]          │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│                      Group Operation                           │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  Input: [{dept:"sales", salary:75000}, ...]                  │
│  Group: @[dept]                                               │
│                                                                │
│  Steps:                                                        │
│    1. Build group key for each row                             │
│    2. Collect rows into groups by key                          │
│    3. Store groups in context                                  │
│    4. Output one row per group with group keys                 │
│                                                                │
│  Groups in Context:                                            │
│    "sales" → [{...}, {...}, ...]                              │
│    "engineering" → [{...}, {...}]                             │
│                                                                │
│  Output: [{dept:"sales"}, {dept:"engineering"}]              │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│                  Aggregate Select Operation                    │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  Input: Group result + Context with groups                    │
│  Select: ![dept; avg_sal:avg[salary]; cnt:count[id]]        │
│                                                                │
│  For each group:                                               │
│    1. Set current group in context                             │
│    2. Evaluate each column:                                    │
│       - Group columns: take from first row                     │
│       - Aggregate functions: evaluate over all rows in group   │
│    3. Add row to result                                        │
│                                                                │
│  Output: [{dept:"sales", avg_sal:71500, cnt:2}, ...]        │
└────────────────────────────────────────────────────────────────┘

```

## Memory Model

```
┌─────────────────────────────────────────────────────────────────┐
│                           Tables                                │
│                   (Registered in Context)                       │
│                                                                 │
│  employees → ArrayList<LinkedHashMap<String, Object>>          │
│               ├─ {id:1, name:"Alice", ...}                     │
│               ├─ {id:2, name:"Bob", ...}                       │
│               └─ {id:3, name:"Charlie", ...}                   │
│                                                                 │
│  sales → ArrayList<LinkedHashMap<String, Object>>              │
│          ├─ {id:1, product:"Widget A", ...}                    │
│          └─ {id:2, product:"Widget B", ...}                    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼ Operation creates new list
┌─────────────────────────────────────────────────────────────────┐
│                     Intermediate Results                        │
│              (New list for each operation)                      │
│                                                                 │
│  After Filter: new ArrayList<Map>                              │
│  After Select: new ArrayList<Map> (different structure)        │
│  After Sort: new ArrayList<Map> (reordered)                    │
│                                                                 │
│  Note: Original data unchanged (immutable operations)           │
└─────────────────────────────────────────────────────────────────┘

```

## Type System

```
┌──────────────────────────────────────────────────────────────────┐
│                        PRQL-Q Type System                        │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Java Type       │  PRQL-Q Syntax   │  Example                  │
│  ───────────────────────────────────────────────────────────── │
│  Double          │  42, 3.14        │  salary: 75000.0          │
│  String          │  "text"          │  name: "Alice"            │
│  String (Symbol) │  `sales          │  dept: "sales"            │
│  Boolean         │  implicit        │  age > 30 → true          │
│  null            │  null            │  missing value            │
│                                                                  │
│  Conversions:                                                    │
│    Number ↔ String  : tostring[], tonumber[]                    │
│    Boolean ↔ Number : 0 = false, non-zero = true                │
│    Null handling    : Propagates through expressions            │
└──────────────────────────────────────────────────────────────────┘

```

This architecture provides:
- **Modularity**: Each component has clear responsibilities
- **Extensibility**: Easy to add new operations and functions
- **Performance**: Optimization at plan level
- **Maintainability**: Clean separation of parsing, planning, execution
- **Type Safety**: Runtime type checking with automatic conversions
