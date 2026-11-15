import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { afterEach, describe, expect, it } from 'vitest';
import { AuthError } from '../src/auth/principal.js';
import { HttpError, Router, readJson, sendJson } from '../src/http/router.js';

let server: Server | null = null;

async function start(router: Router): Promise<string> {
  server = createServer((request, response) => void router.handle(request, response));
  await new Promise<void>((resolve) => server!.listen(0, '127.0.0.1', resolve));
  return `http://127.0.0.1:${(server!.address() as AddressInfo).port}`;
}

afterEach(async () => {
  await new Promise<void>((resolve) => (server ? server.close(() => resolve()) : resolve()));
  server = null;
});

describe('Router', () => {
  it('responde JSON con CORS solo para orígenes permitidos y CORP cross-origin', async () => {
    const base = await start(new Router(['https://erp.example']).get('/health', (_req, res) => sendJson(res, 200, { status: 'UP' })));

    const allowed = await fetch(`${base}/health`, { headers: { Origin: 'https://erp.example' } });
    expect(allowed.status).toBe(200);
    expect(await allowed.json()).toEqual({ status: 'UP' });
    expect(allowed.headers.get('access-control-allow-origin')).toBe('https://erp.example');
    expect(allowed.headers.get('cross-origin-resource-policy')).toBe('cross-origin');
    expect(allowed.headers.get('access-control-expose-headers')).toContain('ETag');

    const other = await fetch(`${base}/health`, { headers: { Origin: 'https://evil.example' } });
    expect(other.headers.get('access-control-allow-origin')).toBeNull();
  });

  it('atiende preflight, 404, 405 y traduce errores de autenticación y de negocio', async () => {
    const router = new Router(['*'])
      .get('/auth', () => {
        throw new AuthError('Falta el token de acceso', 401);
      })
      .post('/echo', async (req, res) => sendJson(res, 200, await readJson(req)))
      .get('/boom', () => {
        throw new HttpError(409, 'Conflicto');
      });
    const base = await start(router);

    const preflight = await fetch(`${base}/echo`, { method: 'OPTIONS', headers: { Origin: 'http://localhost:4200' } });
    expect(preflight.status).toBe(204);
    expect(preflight.headers.get('access-control-allow-headers')).toContain('Authorization');

    expect((await fetch(`${base}/nada`)).status).toBe(404);
    const wrongMethod = await fetch(`${base}/echo`);
    expect(wrongMethod.status).toBe(405);
    expect(wrongMethod.headers.get('allow')).toBe('POST');

    const unauthorized = await fetch(`${base}/auth`);
    expect(unauthorized.status).toBe(401);
    expect(((await unauthorized.json()) as { detail: string }).detail).toBe('Falta el token de acceso');
    expect((await fetch(`${base}/boom`)).status).toBe(409);

    const echo = await fetch(`${base}/echo`, { method: 'POST', body: JSON.stringify({ a: 1 }) });
    expect(await echo.json()).toEqual({ a: 1 });
    expect((await fetch(`${base}/echo`, { method: 'POST', body: '{roto' })).status).toBe(400);
  });
});
