# Use official OpenJDK 17 slim image
FROM eclipse-temurin:17-jdk-alpine

# Set working directory
WORKDIR /app

# Keep the application working directory separate from the frontend source.
WORKDIR /app/backend
COPY backend/src/main/java/ src/main/java/
COPY frontend/ web/

# Create build output and runtime data directories
RUN mkdir -p build/classes data

# Compile all Java source files
RUN find src/main/java -name "*.java" > /tmp/sources.txt && \
    javac -encoding UTF-8 -d build/classes @/tmp/sources.txt && \
    rm /tmp/sources.txt

# Expose the port (Render uses PORT env variable)
EXPOSE 8080

# Start the server – use PORT env var if set (Render), otherwise 8080
CMD ["sh", "-c", "java -cp build/classes com.airport.baggage.Main ${PORT:-8080}"]
