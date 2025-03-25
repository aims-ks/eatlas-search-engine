# Use Maven to build the application
FROM maven:3.8.5-jdk-11 AS builder
WORKDIR /app

# Copy pom.xml to install dependencies and for compilation
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the source code
COPY src ./src

# Build the war file
RUN mvn package -DskipTests

# Use the official Tomcat image as the base
FROM tomcat:10.1.39-jre11

ENV TZ=Australia/Brisbane
ENV EATLAS-SEARCH-ENGINE_CONFIG_FILE=/usr/local/tomcat/searchengine-data/eatlas_search_engine.json

# Copy the WAR file from the builder stage to the Tomcat webapps directory
COPY --from=builder /app/target/eatlas-search-engine.war /usr/local/tomcat/webapps/

COPY tomcat-conf /usr/local/tomcat/conf

# Expose the default Tomcat port
EXPOSE 8080
