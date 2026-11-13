# --- Etapa 1: compilación con Maven Wrapper ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src/ src/
RUN ./mvnw -q -B package -DskipTests

# --- Etapa 2: imagen de ejecución ---
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 erp
COPY --from=build /app/target/insurance-api-*.jar app.jar
USER erp
EXPOSE 8080
# Sin SPRING_PROFILES_ACTIVE arranca el perfil demo; usa "prod" con PostgreSQL (ver docker-compose.yml).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
