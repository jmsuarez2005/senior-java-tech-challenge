# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle sh gradlew dependencies --no-daemon -q > /dev/null
COPY src src
RUN --mount=type=cache,target=/root/.gradle sh gradlew quarkusBuild --no-daemon -q

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/build/quarkus-app ./
RUN chown app:app /app
USER app
ENV JAVA_OPTS="-XX:+UseSerialGC -XX:MaxRAMPercentage=75 -XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=/app/app.jsa"
RUN timeout -s TERM 15 java $JAVA_OPTS -jar quarkus-run.jar > /dev/null 2>&1; test -f /app/app.jsa
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar quarkus-run.jar"]
