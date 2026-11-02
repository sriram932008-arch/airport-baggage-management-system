# Use official OpenJDK 17 slim image
FROM eclipse-temurin:17-jdk-alpine

# Set working directory inside the container
WORKDIR /app

# Copy Java source code
COPY backend/src/main/java/ src/

# Copy web assets into the location the server expects (web/)
COPY backend/web/ web/

# Create build output and data directories
RUN mkdir -p bin data

# Compile all Java source files
RUN find src -name "*.java" > sources.txt && \
    javac -encoding UTF-8 -d bin @sources.txt && \
    rm sources.txt

# Expose default port (Render injects PORT env var)
EXPOSE 8080

# Run the server — PORT env var is read by Main.java
CMD ["sh", "-c", "java -cp bin com.airport.baggage.Main"]
