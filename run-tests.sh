#!/bin/bash

echo "Running Yjs4j Unit Tests..."
echo

# Compile the project
echo "Compiling project..."
mvn clean compile test-compile

if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

echo
echo "Running all tests..."
mvn test

if [ $? -ne 0 ]; then
    echo "Tests failed!"
    exit 1
fi

echo
echo "All tests passed successfully!"