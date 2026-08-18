# ---- Build stage ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :api:war --no-daemon

# ---- Run stage ----
FROM tomcat:9.0-jdk17
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /workspace/api/build/libs/api-1.0-SNAPSHOT.war /usr/local/tomcat/webapps/ROOT.war
ENV NTROPY_CONFIG_DIR=/usr/local/tomcat/conf/ntropy
RUN mkdir -p "$NTROPY_CONFIG_DIR"
EXPOSE 8080
CMD ["catalina.sh", "run"]
