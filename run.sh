#!/usr/bin/env bash
ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$ROOT_DIR/backend/web"
cp -R "$ROOT_DIR/frontend/." "$ROOT_DIR/backend/web/"
cd "$ROOT_DIR/backend" || exit 1

echo "======================================================================="
echo "  Intelligent Airport Baggage Handling and Routing System"
echo "======================================================================="
echo ""

mkdir -p build/classes
echo "Compiling Java source files..."
javac -encoding UTF-8 -d build/classes $(find src/main/java -name "*.java")

if [ $? -ne 0 ]; then
    echo "[ERROR] Java compilation failed."
    exit 1
fi

echo "Compilation successful."
echo "Starting server on http://localhost:8080 ..."
java -cp build/classes com.airport.baggage.Main 8080
