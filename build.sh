#!/bin/bash

# PRQL-Q Build and Run Script

set -e

echo "============================================"
echo "PRQL-Q: Building and Running"
echo "============================================"
echo

# Clean previous build
if [ -d "out" ]; then
    echo "Cleaning previous build..."
    rm -rf out
fi

# Create output directory
mkdir -p out

# Compile all Java files
echo "Compiling Java sources..."
javac -d out \
    com/prqlq/Lexer.java \
    com/prqlq/ASTNode.java \
    com/prqlq/Parser.java \
    com/prqlq/QueryContext.java \
    com/prqlq/QueryPlanner.java \
    com/prqlq/QueryExecutor.java \
    com/prqlq/PRQLQ.java \
    com/prqlq/PRQLQExamples.java

echo "✓ Compilation successful"
echo

# Run examples
echo "Running examples..."
echo "============================================"
java -cp out com.prqlq.PRQLQExamples

echo
echo "============================================"
echo "Build and execution completed successfully!"
echo "============================================"
