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
# Opciones de la JVM; Render las sobrescribe desde render.yaml. El puerto llega en PORT (o SERVER_PORT).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
# Sin SPRING_PROFILES_ACTIVE arranca el perfil demo; "supabase" o "prod" usan PostgreSQL.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
