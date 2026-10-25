# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle sh gradlew dependencies --no-daemon -q > /dev/null
COPY src src
RUN --mount=type=cache,target=/root/.gradle sh gradlew bootJar --no-daemon -q \
    && mv build/libs/products-*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --destination /extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /extracted ./
RUN chown app:app /app
USER app
ENV JAVA_OPTS="-XX:+UseSerialGC -XX:MaxRAMPercentage=75 -XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=/app/app.jsa"
# Training run: starts the context and exits, leaving a CDS archive in the image.
RUN java $JAVA_OPTS -Dspring.context.exit=onRefresh -jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
