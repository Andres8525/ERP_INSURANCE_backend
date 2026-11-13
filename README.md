# ERP Insurance API

API REST con Spring Boot 3.5, Java 21 y Maven para un ERP de corredora de seguros médicos ACA.

## Perfiles

| Perfil | Base de datos | Esquema | Datos | Cuándo usarlo |
| --- | --- | --- | --- | --- |
| `demo` (por defecto) | H2 en memoria | Hibernate `create-drop` | `DemoDataSeeder` carga usuarios, pacientes, pólizas, siniestros y anomalías | Desarrollo y presentación. No necesita variables de entorno. |
| `prod` | PostgreSQL | Flyway (`db/migration`) + `ddl-auto: validate` | Ninguno | Despliegue real. Requiere las variables de [.env.example](.env.example). |

Los secretos del perfil demo están en [application-demo.yml](src/main/resources/application-demo.yml) y son públicos: no lo uses con datos reales.

## Inicio rápido (demo)

```powershell
cd backend
.\mvnw.cmd spring-boot:run      # Windows
./mvnw spring-boot:run          # Linux/macOS
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
3. `SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run`. Flyway aplica `V1` y `V2` al arrancar.
4. El primer administrador se crea con `BOOTSTRAP_ADMIN_EMAIL` y `BOOTSTRAP_ADMIN_PASSWORD` (mínimo 16 caracteres) solo si no existe ningún usuario. Elimina esas variables después.

## Supabase

1. Copia `.env.example` a `.env` y completa `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `ENCRYPTION_KEY` y `JWT_SECRET` (el `.env` ya activa `spring.profiles.active=supabase`).
2. Usa la cadena del **Session pooler** (IPv4, puerto 5432) con `?sslmode=require`; el usuario es `postgres.<project-ref>`.
3. `.\mvnw.cmd spring-boot:run`: Flyway crea el esquema `erp` y aplica V1/V2. Con `DEMO_SEED=true` se cargan los datos demo la primera vez.

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

## Docker

```powershell
docker compose up --build     # PostgreSQL 16 + API en perfil prod (Flyway V1/V2)
docker build -t erp-insurance-api . && docker run -p 8080:8080 erp-insurance-api   # perfil demo
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
| POST | `/api/audit/resolve-batch` | `{ids: [uuid], action: "ARCHIVAR_DEFINITIVO" \| "AUTOCORREGIR"}` → 200 `{processed, action, ids}` |

Notas del contrato:

- Las respuestas paginadas usan `PageResponse`: `content`, `totalElements`, `totalPages`, `number` (desde 0), `size`, `first`, `last`.
- Jackson usa `non_null`: los campos nulos **no aparecen** en el JSON.
- `resolve-batch` es atómico (máx. 100 IDs, solo anomalías abiertas). `AUTOCORREGIR` solo admite `Patient.email`, `Patient.phone` y `Policy.premiumAmount`; cada anomalía expone `autoCorrectable` para que la UI lo sepa de antemano.
- Los errores siguen RFC 7807 (`ProblemDetail`: `status`, `title`, `detail`). Las validaciones añaden `errors` (campo → mensaje); JSON mal formado, enums inválidos y campos desconocidos devuelven 400 con un `detail` explicativo; conflictos de integridad, 409.
- Swagger UI (`/api/docs`) agrupa los endpoints por módulo y permite autenticarse con el botón *Authorize* (Bearer JWT).

Los tipos TypeScript equivalentes están en `fronted/Erp_Insurance/src/app/core/api/api.models.ts`; si cambias un DTO, cambia ambos.

## Pruebas

```powershell
.\mvnw.cmd test
```

- `ApiIntegrationTest`: levanta el perfil demo y recorre el contrato que usa el frontend (CORS desde `localhost:4200`, login JWT, anomalías + resolución masiva, CRUD de pacientes, pólizas y siniestros, 401 sin token).
- `SecurityRulesTest`: roles (AGENT sin auditoría), token alterado, credenciales inválidas y `/api/auth/me`.
- `ErrorHandlingTest`: respuestas 4xx con `ProblemDetail` (nunca 500 por errores del cliente).
- `PolicyApiTest`, `ClaimQueryTest`: solapes de cobertura, actualización/borrado de pólizas y filtros de siniestros.
- `ClaimEntityTest`, `AutoCorrectionRuleTest`, `SensitiveDataConverterTest`: pruebas unitarias sin Spring.

GitHub Actions (`.github/workflows/ci.yml`) ejecuta `./mvnw verify` en cada push a `main` y en cada pull request.
