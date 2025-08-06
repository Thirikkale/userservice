# Multi-stage build for smaller final image
FROM openjdk:17-jdk-slim as builder

WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Copy pom.xml first for better Docker layer caching
COPY pom.xml .

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Production stage
FROM openjdk:17-jdk-slim

WORKDIR /app

# Install Python and required packages for your AI services
RUN apt-get update && \
    apt-get install -y python3 python3-pip python3-venv && \
    rm -rf /var/lib/apt/lists/*

# Create Python virtual environment
RUN python3 -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Copy Python scripts and install dependencies
COPY python_scripts/ ./python_scripts/
RUN if [ -f python_scripts/requirements.txt ]; then \
        pip install --no-cache-dir -r python_scripts/requirements.txt; \
    else \
        pip install --no-cache-dir fastapi uvicorn opencv-python pillow numpy tensorflow easyocr face-recognition; \
    fi

# Copy the built jar from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Create uploads directory
RUN mkdir -p uploads/driver-documents uploads/selfies uploads/temp

# Create non-root user for security
RUN addgroup --system spring && adduser --system spring --ingroup spring
RUN chown -R spring:spring /app
USER spring

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8081/user-service/api/v1/health || exit 1

EXPOSE 8081

# Use environment variables for configuration
ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="-Xmx512m -Xms256m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]