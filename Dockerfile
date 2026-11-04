FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY . .
RUN mvn --batch-mode --no-transfer-progress -pl api -am -DskipTests package
RUN java -Djarmode=layertools -jar api/target/api-1.0.0-SNAPSHOT.jar extract --destination /layers

FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app
RUN useradd --system --uid 10001 --create-home spring
COPY --from=build --chown=10001:10001 /layers/dependencies/ ./
COPY --from=build --chown=10001:10001 /layers/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /layers/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /layers/application/ ./

USER 10001:10001
EXPOSE 8443
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]