#!/usr/bin/env bash
echo "======================================================================="
echo "  Intelligent Airport Baggage Handling and Routing System"
echo "======================================================================="
echo ""

mkdir -p bin
echo "Compiling Java source files..."
javac -d bin $(find src -name "*.java")

if [ $? -ne 0 ]; then
    echo "[ERROR] Java compilation failed."
    exit 1
fi

echo "Compilation successful."
echo "Starting server on http://localhost:8080 ..."
java -cp bin com.airport.baggage.Main 8080

