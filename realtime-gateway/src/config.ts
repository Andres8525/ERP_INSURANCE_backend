import { hostname } from 'node:os';
import { randomUUID } from 'node:crypto';

export interface GatewayConfig {
  port: number;
  instanceId: string;
  jwtSecret: string;
  jwtIssuer: string;
  allowedOrigins: string[];
  redisUrl: string | null;
  redisConnectTimeoutMs: number;
  heartbeatIntervalMs: number;
  sseHeartbeatMs: number;
  vitalsIntervalMs: number;
  sseReplayBuffer: number;
  fanOutBatchSize: number;
  maxBufferedBytes: number;
  maxPayloadBytes: number;
  lockTtlMs: number;
  pollingIntervalMs: number;
}

const DEMO_SECRET = 'demo-only-jwt-secret-change-me-0123456789abcdef';

function integer(env: NodeJS.ProcessEnv, name: string, fallback: number, min = 1): number {
  const raw = env[name];
  if (raw === undefined || raw.trim() === '') return fallback;
  const value = Number.parseInt(raw, 10);
  if (!Number.isFinite(value) || value < min) {
    throw new Error(`${name} debe ser un entero >= ${min} (valor recibido: ${raw})`);
  }
  return value;
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): GatewayConfig {
  const jwtSecret = env.JWT_SECRET?.trim() || (env.NODE_ENV === 'production' ? '' : DEMO_SECRET);
  if (Buffer.byteLength(jwtSecret, 'utf8') < 32) {
    throw new Error('JWT_SECRET debe tener al menos 32 bytes y coincidir con el de insurance-api');
  }
  return {
    port: integer(env, 'PORT', 8090),
    instanceId: env.INSTANCE_ID?.trim() || `${hostname()}-${randomUUID().slice(0, 8)}`,
    jwtSecret,
    jwtIssuer: env.JWT_ISSUER?.trim() || 'erp-insurance-api',
    allowedOrigins: (env.FRONTEND_ORIGINS ?? 'http://localhost:4200')
      .split(',')
      .map((origin) => origin.trim())
      .filter(Boolean),
    redisUrl: env.REDIS_URL?.trim() || null,
    redisConnectTimeoutMs: integer(env, 'REDIS_CONNECT_TIMEOUT_MS', 3000),
    heartbeatIntervalMs: integer(env, 'WS_HEARTBEAT_MS', 30000, 50),
    sseHeartbeatMs: integer(env, 'SSE_HEARTBEAT_MS', 15000, 50),
    vitalsIntervalMs: integer(env, 'VITALS_INTERVAL_MS', 1000, 20),
    sseReplayBuffer: integer(env, 'SSE_REPLAY_BUFFER', 120),
    fanOutBatchSize: integer(env, 'FANOUT_BATCH_SIZE', 256),
    maxBufferedBytes: integer(env, 'WS_MAX_BUFFERED_BYTES', 1_048_576),
    maxPayloadBytes: integer(env, 'WS_MAX_PAYLOAD_BYTES', 64 * 1024),
    lockTtlMs: integer(env, 'RECORD_LOCK_TTL_MS', 120000, 100),
    pollingIntervalMs: integer(env, 'POLLING_INTERVAL_MS', 3000, 100),
  };
}
