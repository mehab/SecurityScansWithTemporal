# Security Scan Worker Dockerfile
# Builds a container image for running security scan workers on Kubernetes

FROM openjdk:11-jre-slim

# Install system dependencies
RUN apt-get update && apt-get install -y \
    git \
    curl \
    wget \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Install BlackDuck Detect
# The detect script will download the actual detect jar on first run
RUN curl -L https://detect.synopsys.com/detect.sh -o /usr/local/bin/detect.sh && \
    chmod +x /usr/local/bin/detect.sh

# Create workspace directory
RUN mkdir -p /workspace/security-scans && \
    chmod 755 /workspace/security-scans

# Copy application JAR
COPY target/security-scan-1.0.0.jar /app/security-scan.jar

# Set working directory
WORKDIR /app

# Environment variables
ENV WORKSPACE_BASE_DIR=/workspace/security-scans
ENV TEMPORAL_ADDRESS=localhost:7233

# Health check (optional)
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD pgrep -f "security-scan.jar" || exit 1

# Run the worker
ENTRYPOINT ["java", "-jar", "security-scan.jar"]

