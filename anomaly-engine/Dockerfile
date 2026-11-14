FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY grpc-contracts/ grpc-contracts/
COPY anomaly-engine/ anomaly-engine/
COPY insurance-api/pom.xml insurance-api/pom.xml
RUN chmod +x mvnw && ./mvnw -q -B -pl anomaly-engine -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 engine
COPY --from=build /app/anomaly-engine/target/anomaly-engine-*-exec.jar /app/anomaly-engine.jar
USER engine
EXPOSE 9090
ENV GRPC_PORT=9090 JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/anomaly-engine.jar"]
