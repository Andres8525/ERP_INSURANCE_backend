FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY grpc-contracts/ grpc-contracts/
COPY anomaly-engine/ anomaly-engine/
COPY insurance-api/ insurance-api/
RUN chmod +x mvnw && ./mvnw -q -B -pl insurance-api -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 erp
COPY --from=build /app/insurance-api/target/insurance-api-*.jar /tmp/app.jar
RUN java -Djarmode=tools -jar /tmp/app.jar extract --destination /app && rm /tmp/app.jar
RUN java -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:ArchiveClassesAtExit=/app/app.jsa -Dspring.context.exit=onRefresh -jar /app/app.jar && chown -R erp /app
USER erp
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -XX:SharedArchiveFile=/app/app.jsa -jar /app/app.jar"]
