FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw -B dependency:go-offline
COPY src ./src
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 calipso \
    && useradd --system --uid 10001 --gid calipso --home-dir /app calipso
COPY --from=build --chown=calipso:calipso /workspace/target/calipso-0.0.1-SNAPSHOT.jar app.jar
USER calipso
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
