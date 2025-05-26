FROM maven:3.8-jdk-8

WORKDIR /app

# Copy the POM file first to cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the rest of the application
COPY src ./src

# Build the application
RUN mvn clean package

# Run the worker using the shaded JAR
CMD ["java", "-jar", "target/cadence-eats-1.0-SNAPSHOT.jar"] 