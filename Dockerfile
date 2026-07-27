FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle bootJar --no-daemon --console=plain

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/sql-performance-analyzer-0.0.1-SNAPSHOT.jar /app/app.jar
EXPOSE 18881
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
