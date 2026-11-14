# ERP Insurance API

API REST con Spring Boot 3.5, Java 21 y Maven para un ERP de corredora de seguros médicos ACA, con un microservicio gRPC de detección de anomalías.

## Arquitectura

```text
backend/
├── pom.xml              pom padre: módulos, versiones de gRPC y protobuf
├── grpc-contracts/      contratos .proto versionados (erp.anomaly.v1) y stubs generados
├── anomaly-engine/      microservicio gRPC sin Spring: reglas de detección + servidor
└── insurance-api/       API REST Spring Boot (JWT, JPA, Flyway) y cliente gRPC
```

```text
Angular ──HTTP/JSON──▶ insurance-api ──gRPC (protobuf)──▶ anomaly-engine
                           │  POST /api/audit/scan            Analyze / AnalyzeStream
                           └─ si no hay microservicio: el mismo motor en proceso (InProcessServer)
```

Patrones aplicados:

| Patrón | Dónde |
| --- | --- |
| Contract-first con versionado de paquete | `grpc-contracts/src/main/proto/erp/anomaly/v1/anomaly_engine.proto` |
| RPC unario y server-streaming | `Analyze` (la API) y `AnalyzeStream` (clientes que procesan hallazgos uno a uno) |
| Interceptores de cliente y servidor | `CorrelationIdClientInterceptor` (vía `CallOptions`) y `CorrelationIdServerInterceptor` propagan `x-correlation-id` y registran duración |
| Deadlines y reintentos | deadline por llamada (`ANOMALY_ENGINE_DEADLINE`) y política de reintentos ante `UNAVAILABLE` en la service config del canal |
| Fallback | si el microservicio no responde (`UNAVAILABLE`, `DEADLINE_EXCEEDED`), la API usa el motor en proceso |
| Health checking y reflexión | `grpc.health.v1.Health` y `ProtoReflectionServiceV1` en el microservicio (compatible con `grpcurl`) |
| Puertos y adaptadores | `AnomalyDetector` (puerto) y `GrpcAnomalyDetector` (adaptador) |
| Capa anticorrupción | `SnapshotMapper` (JPA → protobuf) y `ProtoMapper` (protobuf → dominio del motor) |
| Strategy y Builder | cada `DetectionRule` es una estrategia; `Finding.builder()` construye hallazgos |

Reglas del motor: correo inválido o con dominio mal escrito (`piedad@gmailcom` → `piedad@gmail.com`), teléfono fuera de formato, fecha de nacimiento imposible, pacientes duplicados, pólizas solapadas, pólizas vencidas marcadas como activas y prima fuera de rango. Las correcciones de correo y teléfono se pueden aplicar con `AUTOCORREGIR`.

En Render solo se despliega `insurance-api` (el plan free no admite servicios privados) y el motor corre en proceso; con `docker compose` corre como microservicio independiente.

## Perfiles

| Perfil | Base de datos | Esquema | Datos | Cuándo usarlo |
| --- | --- | --- | --- | --- |
| `demo` (por defecto) | H2 en memoria | Hibernate `create-drop` | `DemoDataSeeder` carga usuarios, pacientes, pólizas, siniestros y anomalías | Desarrollo y presentación. No necesita variables de entorno. |
| `prod` | PostgreSQL | Flyway (`db/migration`) + `ddl-auto: validate` | Ninguno | Despliegue real. Requiere las variables de [.env.example](.env.example). |

Los secretos del perfil demo están en [application-demo.yml](insurance-api/src/main/resources/application-demo.yml) y son públicos: no lo uses con datos reales.

## Inicio rápido (demo)

```powershell
cd backend
.\mvnw.cmd install -DskipTests                  # compila contratos, motor y API
.\mvnw.cmd -pl insurance-api spring-boot:run    # Windows
./mvnw -pl insurance-api spring-boot:run         # Linux/macOS
```

Microservicio gRPC por separado (opcional):

```powershell
java -jar anomaly-engine/target/anomaly-engine-1.0.0-exec.jar      # escucha en GRPC_PORT (9090)
$env:ANOMALY_ENGINE_ADDRESS="localhost:9090"; .\mvnw.cmd -pl insurance-api spring-boot:run
```

Requiere JDK 21 (`JAVA_HOME`). El wrapper descarga Maven 3.9.12 si no está instalado. La API escucha en `http://localhost:8080` (cámbialo con `SERVER_PORT`) y Swagger UI queda en `http://localhost:8080/api/docs`.

Usuarios demo (contraseña `NexoDemo2026!`):

| Email | Rol | Auditoría IA |
| --- | --- | --- |
| `admin@nexo.demo` | ADMIN | Sí |
| `auditor@nexo.demo` | AUDITOR | Sí |
| `agente@nexo.demo` | AGENT | No (403) |

## Inicio con PostgreSQL (prod)

1. Crea la base y exporta las variables de [.env.example](.env.example) (Spring no lee archivos `.env`).
2. `ENCRYPTION_KEY`: 32 bytes aleatorios en Base64 (`openssl rand -base64 32`). `JWT_SECRET`: al menos 32 bytes.
3. `SPRING_PROFILES_ACTIVE=prod ./mvnw -pl insurance-api spring-boot:run`. Flyway aplica `V1` y `V2` al arrancar.
4. El primer administrador se crea con `BOOTSTRAP_ADMIN_EMAIL` y `BOOTSTRAP_ADMIN_PASSWORD` (mínimo 16 caracteres) solo si no existe ningún usuario. Elimina esas variables después.

## Supabase

1. Copia `.env.example` a `.env` y completa `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `ENCRYPTION_KEY` y `JWT_SECRET` (el `.env` ya activa `spring.profiles.active=supabase`).
2. Usa la cadena del **Session pooler** (IPv4, puerto 5432) con `?sslmode=require`; el usuario es `postgres.<project-ref>`.
3. `.\mvnw.cmd -pl insurance-api spring-boot:run`: Flyway crea el esquema `erp` y aplica V1/V2. Con `DEMO_SEED=true` se cargan los datos demo la primera vez.

Las tablas viven en el esquema `erp`, no en `public`, para que la Data API de Supabase no las exponga. Guarda `ENCRYPTION_KEY` en un lugar seguro: sin ella los datos cifrados no se pueden leer.

## Despliegue en Render

El repo incluye [render.yaml](render.yaml) (Blueprint): servicio web Docker, plan free, región Oregón (la misma que Supabase) y health check en `/actuator/health`.

1. En Render: **New → Blueprint**, conecta este repositorio y acepta `render.yaml`.
2. Completa las variables marcadas como secretas: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `ENCRYPTION_KEY` (la **misma** del `.env` local), `JWT_SECRET` y `FRONTEND_ORIGINS`.
3. Render construye la imagen y arranca la API; la URL pública queda como `https://<servicio>.onrender.com`.

Notas:

- La app escucha en el puerto que Render entrega en `PORT` y respeta las cabeceras `X-Forwarded-*` de su proxy HTTPS.
- `FRONTEND_ORIGINS` acepta patrones: `https://mi-app.vercel.app,https://*.vercel.app` cubre producción y vistas previas.
- El plan free se suspende tras ~15 min sin tráfico; la primera petición posterior tarda alrededor de un minuto.
- `JAVA_OPTS` ajusta la JVM a los 512 MB del plan free.

## Rendimiento y disponibilidad en Render

- **Keep-alive:** `.github/workflows/keep-alive.yml` hace ping a `/actuator/health` cada 10 min para que Render free no suspenda el servicio (y Supabase no pause el proyecto). Se puede lanzar a mano desde *Actions → Keep alive → Run workflow*.
- **Login rápido:** BCrypt con coste 10 (`BCRYPT_STRENGTH`). Los hashes con otro coste se re-cifran automáticamente en el siguiente login correcto.
- **Arranque en frío más corto:** la imagen ejecuta el JAR desempaquetado con un archivo CDS (`app.jsa`) generado durante el build.
- **Menos consultas y menos bytes:** `default_batch_fetch_size` elimina consultas N+1 y las respuestas JSON van comprimidas con gzip.
- **Autorrecuperación:** `-XX:+ExitOnOutOfMemoryError` hace que Render reinicie el servicio si la JVM se queda sin memoria; el health check de Render también lo reinicia si deja de responder.

## Docker

```powershell
docker compose up --build     # PostgreSQL 16 + anomaly-engine (gRPC :9090) + API en perfil prod
docker build -t erp-insurance-api . && docker run -p 8080:8080 erp-insurance-api   # API sola, perfil demo
docker build -f anomaly-engine/Dockerfile -t erp-anomaly-engine .                   # microservicio gRPC
```

`docker-compose.yml` crea el administrador `admin@nexo.local` / `LocalAdminPassword2026!` y usa secretos de desarrollo: no lo uses tal cual en producción.

## Seguridad

- JWT HS256 (`POST /api/auth/login`); todos los endpoints excepto login, OpenAPI y health exigen `Authorization: Bearer <token>`.
- CORS por lista blanca (`FRONTEND_ORIGINS`, por defecto `http://localhost:4200,http://localhost:3000`); métodos GET, POST, PUT, PATCH, DELETE y OPTIONS.
- `/api/audit/**` solo para roles ADMIN y AUDITOR.
- Nombres, email, teléfono, fecha de nacimiento, ingreso, número de póliza, descripciones y valores de anomalías se cifran con AES-256-GCM en la capa JPA; el email se busca mediante un HMAC separado. Contraseñas con BCrypt.

Esto es una base técnica, no una certificación HIPAA: producción requiere TLS, gestión de secretos, aislamiento por organización, auditoría de accesos a PHI, retención, backups, respuesta a incidentes y BAAs.

## Endpoints

| Método | Ruta | Descripción |
| --- | --- | --- |
| POST | `/api/auth/login` | `{email, password}` → `{accessToken, tokenType, expiresAt, email, role}` |
| GET | `/api/auth/me` | Sesión actual: `{email, role, expiresAt}` |
| GET | `/api/patients?page=0&size=25&accountStatus=` | Pacientes paginados (`PageResponse`) |
| POST | `/api/patients` | Alta de paciente (201) |
| GET | `/api/patients/{id}` | Detalle |
| PUT / PATCH | `/api/patients/{id}` | Actualización parcial (solo campos enviados) |
| DELETE | `/api/patients/{id}` | 204; 409 si tiene siniestros asociados |
| GET | `/api/patients/{id}/policies` | Pólizas del paciente |
| POST | `/api/patients/{id}/policies` | Alta de póliza (201); 409 si su cobertura se solapa con otra póliza no terminada |
| PUT / PATCH | `/api/patients/{id}/policies/{policyId}` | Actualización parcial de la póliza |
| DELETE | `/api/patients/{id}/policies/{policyId}` | 204 |
| GET | `/api/claims?status=&patientId=&priority=` | Lista de siniestros filtrable |
| GET | `/api/claims/pipeline` | Siniestros agrupados por `OPEN`, `IN_REVIEW`, `APPROVED`, `REJECTED` |
| GET | `/api/claims/{id}` | Detalle con servicios e historial |
| POST | `/api/claims` | Alta (201), siempre empieza en `OPEN` |
| PUT / PATCH | `/api/claims/{id}` | Actualización parcial; un cambio de estado queda en el historial |
| PATCH | `/api/claims/{id}/status` | `{status, note?}`: mover tarjeta del Kanban |
| DELETE | `/api/claims/{id}` | 204 |
| GET | `/api/audit/anomalies?page=0&size=25&severity=&resolutionStatus=&type=` | Anomalías paginadas (≥5 en demo) |
| GET | `/api/audit/summary` | `{open, critical, review, resolvedThisMonth}` |
| POST | `/api/audit/scan` | Ejecuta el motor gRPC → `{analyzedRecords, findings, created, alreadyKnown, engineMode, engineVersion, appliedRules, correlationId, durationMs}` |
| POST | `/api/audit/resolve-batch` | `{ids: [uuid], action: "ARCHIVAR_DEFINITIVO" \| "AUTOCORREGIR"}` → 200 `{processed, action, ids}` |

Notas del contrato:

- Las respuestas paginadas usan `PageResponse`: `content`, `totalElements`, `totalPages`, `number` (desde 0), `size`, `first`, `last`.
- Jackson usa `non_null`: los campos nulos **no aparecen** en el JSON.
- `resolve-batch` es atómico (máx. 100 IDs, solo anomalías abiertas). `AUTOCORREGIR` solo admite `Patient.email`, `Patient.phone` y `Policy.premiumAmount`; cada anomalía expone `autoCorrectable` para que la UI lo sepa de antemano.
- Los errores siguen RFC 7807 (`ProblemDetail`: `status`, `title`, `detail`). Las validaciones añaden `errors` (campo → mensaje); JSON mal formado, enums inválidos y campos desconocidos devuelven 400 con un `detail` explicativo; conflictos de integridad, 409.
- Swagger UI (`/api/docs`) agrupa los endpoints por módulo y permite autenticarse con el botón *Authorize* (Bearer JWT).

Los tipos TypeScript equivalentes están en `fronted/src/app/core/api/api.models.ts`; si cambias un DTO, cambia ambos.

## Pruebas

```powershell
.\mvnw.cmd verify
```

- `DetectionRulesTest`, `AnomalyEngineGrpcServiceTest`: reglas del motor, orden de prioridad, streaming, `INVALID_ARGUMENT` y eco de la cabecera de correlación.
- `GrpcAnomalyDetectorTest`: llamada remota real por red, fallback en proceso y fallo explícito sin fallback.
- `AnomalyScanTest`: análisis desde la API, deduplicación, corrección automática y validación de correos sin dominio.

- `ApiIntegrationTest`: levanta el perfil demo y recorre el contrato que usa el frontend (CORS desde `localhost:4200`, login JWT, anomalías + resolución masiva, CRUD de pacientes, pólizas y siniestros, 401 sin token).
- `SecurityRulesTest`: roles (AGENT sin auditoría), token alterado, credenciales inválidas y `/api/auth/me`.
- `ErrorHandlingTest`: respuestas 4xx con `ProblemDetail` (nunca 500 por errores del cliente).
- `PolicyApiTest`, `ClaimQueryTest`: solapes de cobertura, actualización/borrado de pólizas y filtros de siniestros.
- `ClaimEntityTest`, `AutoCorrectionRuleTest`, `SensitiveDataConverterTest`: pruebas unitarias sin Spring.

GitHub Actions (`.github/workflows/ci.yml`) compila y prueba todos los módulos con `./mvnw verify` en cada push a `main` y en cada pull request.
