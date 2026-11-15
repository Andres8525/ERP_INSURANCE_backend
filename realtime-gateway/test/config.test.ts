import { describe, expect, it } from 'vitest';
import { loadConfig } from '../src/config.js';

describe('loadConfig', () => {
  it('usa valores por defecto seguros para desarrollo', () => {
    const config = loadConfig({});
    expect(config.port).toBe(8090);
    expect(config.jwtIssuer).toBe('erp-insurance-api');
    expect(config.redisUrl).toBeNull();
    expect(config.allowedOrigins).toEqual(['http://localhost:4200']);
    expect(config.pollingIntervalMs).toBe(3000);
  });

  it('lee la lista de orígenes, Redis y los tiempos del entorno', () => {
    const config = loadConfig({
      PORT: '9001',
      INSTANCE_ID: 'gw-a',
      FRONTEND_ORIGINS: 'https://erp.vercel.app, http://localhost:4200',
      REDIS_URL: 'redis://localhost:6379',
      WS_HEARTBEAT_MS: '5000',
      JWT_SECRET: 'x'.repeat(40),
    });
    expect(config.port).toBe(9001);
    expect(config.instanceId).toBe('gw-a');
    expect(config.allowedOrigins).toEqual(['https://erp.vercel.app', 'http://localhost:4200']);
    expect(config.redisUrl).toBe('redis://localhost:6379');
    expect(config.heartbeatIntervalMs).toBe(5000);
  });

  it('exige un JWT_SECRET propio en producción y rechaza números inválidos', () => {
    expect(() => loadConfig({ NODE_ENV: 'production' })).toThrow(/JWT_SECRET/);
    expect(() => loadConfig({ JWT_SECRET: 'corto' })).toThrow(/32 bytes/);
    expect(() => loadConfig({ PORT: 'abc' })).toThrow(/PORT/);
  });
});
