import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { signHs256 } from '../src/auth/jwt.js';
import { loadConfig, type GatewayConfig } from '../src/config.js';
import type { Router } from '../src/http/router.js';

export const SECRET = 'demo-only-jwt-secret-change-me-0123456789abcdef';

export function testConfig(overrides: Partial<GatewayConfig> = {}): GatewayConfig {
  return { ...loadConfig({ JWT_SECRET: SECRET, INSTANCE_ID: 'gw-test' }), ...overrides };
}

export function tokenFor(userId: string, role = 'ADMIN', email = `${userId}@nexo.demo`): string {
  return signHs256({ iss: 'erp-insurance-api', sub: userId, email, role, exp: Math.floor(Date.now() / 1000) + 600 }, SECRET);
}

export async function listen(router: Router): Promise<{ server: Server; base: string; close: () => Promise<void> }> {
  const server = createServer((request, response) => void router.handle(request, response));
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  return {
    server,
    base,
    close: () => new Promise<void>((resolve) => {
      server.closeAllConnections();
      server.close(() => resolve());
    }),
  };
}

export const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));
