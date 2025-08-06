# Use specific version for reproducibility
FROM maven:3.9.5-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml first for better caching
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage with Ubuntu base - ✅ FIXED: Use Ubuntu instead of Alpine
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Install system dependencies for Python packages
RUN apt-get update && \
    apt-get install -y \
        python3 \
        python3-pip \
        python3-venv \
        python3-dev \
        build-essential \
        curl \
        libgl1-mesa-glx \
        libglib2.0-0 \
        libsm6 \
        libxext6 \
        libxrender-dev \
        libgomp1 \
        libgstreamer1.0-0 \
        cmake \
        pkg-config \
        libhdf5-dev \
        libopenblas-dev \
        liblapack-dev \
        libatlas-base-dev \
        gfortran \
        libjpeg-dev \
        libpng-dev \
        libtiff-dev \
        libavcodec-dev \
        libavformat-dev \
        libswscale-dev \
        libv4l-dev \
        libxvidcore-dev \
        libx264-dev \
        && rm -rf /var/lib/apt/lists/*

# Setup Python environment
RUN python3 -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Upgrade pip and install wheel
RUN pip install --upgrade pip setuptools wheel

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
RUN groupadd -r spring && useradd -r -g spring spring
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