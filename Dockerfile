# Use specific version for reproducibility
FROM maven:3.9.5-eclipse-temurin-17-alpine AS builder

WORKDIR /app

# Copy pom.xml first for better caching
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage with minimal image - ✅ FIXED: Use Eclipse Temurin
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install runtime dependencies in one layer
RUN apk update && \
    apk add --no-cache \
    python3 \
    py3-pip \
    curl \
    bash \
    gcompat \
    libstdc++ \
    && rm -rf /var/cache/apk/*

# Setup Python environment
RUN python3 -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Copy and install Python dependencies
COPY python_scripts/requirements.txt ./python_scripts/
RUN pip install --no-cache-dir -r python_scripts/requirements.txt

# Copy Python scripts
COPY python_scripts/ ./python_scripts/

# Copy jar from build stage
COPY --from=builder /app/target/*.jar app.jar

# Create necessary directories
RUN mkdir -p uploads/{driver-documents,selfies,temp,vehicle-documents}

# Security: Create non-root user
RUN addgroup -g 1001 spring && adduser -D -u 1001 -G spring spring
RUN chown -R spring:spring /app
USER spring

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

EXPOSE 8081

# Environment variables
ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="-Xmx1g -Xms512m -XX:+UseG1GC"
ENV PYTHONPATH="/app/python_scripts"

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]