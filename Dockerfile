FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY . .
RUN mvn --batch-mode --no-transfer-progress -pl api -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app
COPY --from=build /workspace/api/target/api-1.0.0-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]