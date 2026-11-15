# realtime-gateway

Microservicio de tiempo real del ERP médico (Node.js 22 + TypeScript). Valida el mismo JWT HS256 que emite `insurance-api` (`JWT_SECRET` compartido, emisor `erp-insurance-api`) y expone las tres estrategias de comunicación en tiempo real con casos clínicos concretos.

```
                ┌──────────── nginx (round-robin, sin sticky sessions) ────────────┐
 navegador ───▶ │                                                                  │
                └──────┬──────────────────────────────┬────────────────────────────┘
                       ▼                              ▼
              realtime-a (Node)              realtime-b (Node)
              Map<connId, AuthenticatedSocket> Map<connId, AuthenticatedSocket>
                       │  publish / subscribe          │
                       └──────────▶  Redis Pub/Sub  ◀──┘   (fallback: EventEmitter en memoria)
                                   Redis SET NX PX + Lua (bloqueos de historias clínicas)
```

## Comparativa de protocolos

| Estrategia | Endpoint | Caso clínico | Por qué |
| --- | --- | --- | --- |
| Short polling | `POST /api/realtime/lab-jobs` + `GET /api/realtime/lab-status-polling?jobId=` | Resultados de laboratorio y generación de RIPS | Trabajo pesado sin urgencia: el cliente consulta cada 3 s (`X-Poll-Interval`), `ETag`/`304` evita reenviar el mismo estado y la respuesta indica cuántas consultas fueron inútiles |
| SSE | `GET /api/realtime/vitals-stream?beds=UCI-01,UCI-03` | Signos vitales de UCI y avisos de farmacia al panel de enfermería | Flujo unidireccional servidor → cliente sobre HTTP: reconexión automática del navegador, `Last-Event-ID` para recuperar lo perdido, heartbeat `: ping` |
| WebSocket | `ws(s)://…/ws/medical-hub` | Telemedicina, edición colaborativa y bloqueo de historias clínicas, interconsultas, Código Azul, turnos | Bidireccional y full-duplex con estado por conexión |

## Estado por conexión (C10K)

- `ConnectionRegistry` mantiene `Map<string, AuthenticatedSocket>` (connectionId → socket, `userId`, `email`, rol clínico `MEDICO | ENFERMERO | RECEPCION`, `sedeId`, salas, contadores) e índices secundarios por usuario, sede, rol y sala para resolver destinos sin recorrer todas las conexiones.
- El rol clínico sale del rol del ERP (`ADMIN → MEDICO`, `AUDITOR → ENFERMERO`, `AGENT → RECEPCION`); solo un administrador puede elegir otro con `?clinicalRole=`. La sede se elige con `?sede=SEDE-MIA | SEDE-ORL | SEDE-TPA`.
- Ciclo de vida: `Heartbeat` envía `ping` cada `WS_HEARTBEAT_MS` y termina con `terminate()` las conexiones que no respondieron el `pong` anterior; `close` y `error` eliminan la conexión de todos los índices, liberan sus bloqueos de historias clínicas y su cubeta de rate limit (20 mensajes/s, ráfaga de 40).
- El token viaja en el subprotocolo (`new WebSocket(url, ['bearer', token])`) para no quedar en logs de URL; también se acepta `Authorization` o `?access_token=` (necesario en `EventSource`). Se valida `Origin` contra `FRONTEND_ORIGINS`.

## Fan-out sin bloquear el Event Loop

`fanOut()` serializa el mensaje una sola vez y lo envía en lotes de `FANOUT_BATCH_SIZE` (256) sockets, cediendo el Event Loop con `setImmediate` entre lotes. Omite sockets cerrados y los que superan `WS_MAX_BUFFERED_BYTES` (backpressure). Cada réplica devuelve al emisor un `fanout.report` con destinatarios, entregados, lotes, bytes y duración; `EventLoopMonitor` (`perf_hooks.monitorEventLoopDelay`) publica el retraso p99 en `/api/realtime/metrics`. `POST /api/realtime/load-test/terminals?count=5000` (ADMIN) registra terminales virtuales de sala de espera para medir un Código Azul contra miles de conexiones.

## Escalado horizontal

Toda entrega pasa por el backplane: el servidor que recibe el mensaje publica un sobre `{ origin, target, event, payload }` en el canal `erp:medical-hub:events` y cada réplica entrega a sus sockets locales que coinciden con el destino (`user`, `room`, `sede`, `role`, `all`). Así, la interconsulta del Médico 1 conectado a A llega al Médico 2 conectado a B, y B confirma con `consult.delivered`. Los bloqueos y versiones de las historias clínicas viven en Redis (`SET NX PX` y scripts Lua atómicos), así que son coherentes entre réplicas. Sin `REDIS_URL`, o si Redis no responde en `REDIS_CONNECT_TIMEOUT_MS`, el gateway arranca con un `EventEmitter` en memoria (una sola instancia) y lo indica en `/health` y en el mensaje `welcome`.

## Protocolo WebSocket

Cliente → servidor: `ping`, `chat.join | chat.leave | chat.message | chat.typing` (`roomId` `tele-…`), `record.open | record.lock | record.edit | record.unlock` (`recordId` `HC-…`, `field` `motivoConsulta | diagnostico | evolucion | plan`, `baseVersion`), `consult.request | consult.respond`, `alert.code-blue` (`scope` `sede | all`), `turns.call`.

Servidor → cliente: `welcome`, `pong`, `error` (`BAD_REQUEST | FORBIDDEN | NOT_IN_ROOM | RATE_LIMITED`), `chat.joined | chat.presence | chat.message | chat.typing`, `record.state | record.locked | record.lock-denied | record.updated | record.conflict (NOT_LOCKED | STALE) | record.unlocked`, `consult.sent | consult.request | consult.delivered | consult.response`, `alert.code-blue`, `turns.called`, `fanout.report`. Cada evento distribuido incluye `originInstance` y `deliveredBy`.

## Variables de entorno

| Variable | Por defecto | Uso |
| --- | --- | --- |
| `PORT` | `8090` | Puerto HTTP/WS |
| `JWT_SECRET` | demo (obligatorio en producción) | Mismo secreto que `insurance-api` |
| `FRONTEND_ORIGINS` | `http://localhost:4200` | CORS y validación de `Origin` |
| `REDIS_URL` | — | Activa el backplane Redis y los bloqueos distribuidos |
| `INSTANCE_ID` | hostname + sufijo | Identificador de réplica |
| `WS_HEARTBEAT_MS` / `SSE_HEARTBEAT_MS` | `30000` / `15000` | Latidos |
| `FANOUT_BATCH_SIZE` / `WS_MAX_BUFFERED_BYTES` | `256` / `1048576` | Fan-out y backpressure |
| `RECORD_LOCK_TTL_MS` | `120000` | Expiración del bloqueo de una historia clínica |
| `POLLING_INTERVAL_MS` | `3000` | Intervalo recomendado al cliente |

## Uso

```bash
npm install
npm test          # 62 pruebas: auth, polling, SSE, backplane, registro, heartbeat, fan-out, protocolo y dos réplicas
npm run build && npm start
```

Clúster local con Redis, dos réplicas y nginx sin sticky sessions: `docker compose up realtime-lb` (desde `backend/`), y el frontend apunta a `http://localhost:8090`.
