FROM openjdk:17-jdk-slim

WORKDIR /app

COPY pom.xml .
COPY src ./src

# Install Maven
RUN apt-get update && apt-get install -y maven

# Build the application
RUN mvn clean package -DskipTests

# Copy the built jar
RUN cp target/*.jar app.jar

EXPOSE 8081

CMD ["java", "-jar", "app.jar"]