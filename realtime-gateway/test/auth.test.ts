import type { IncomingMessage } from 'node:http';
import { describe, expect, it } from 'vitest';
import { signHs256, verifyHs256 } from '../src/auth/jwt.js';
import { authenticate, extractToken, resolvePrincipal } from '../src/auth/principal.js';

const SECRET = 'demo-only-jwt-secret-change-me-0123456789abcdef';
const ISSUER = 'erp-insurance-api';
const now = Math.floor(Date.now() / 1000);

function token(claims: Record<string, unknown> = {}): string {
  return signHs256({ iss: ISSUER, sub: 'u-1', email: 'admin@nexo.demo', role: 'ADMIN', exp: now + 600, ...claims }, SECRET);
}

function request(headers: Record<string, string> = {}): IncomingMessage {
  return { headers } as unknown as IncomingMessage;
}

describe('verifyHs256', () => {
  it('acepta tokens firmados por insurance-api y rechaza manipulados, expirados o de otro emisor', () => {
    expect(verifyHs256(token(), SECRET, ISSUER).email).toBe('admin@nexo.demo');

    const [header, , signature] = token().split('.');
    const forged = Buffer.from(JSON.stringify({ iss: ISSUER, sub: 'u-2', role: 'ADMIN', exp: now + 600 })).toString('base64url');
    expect(() => verifyHs256(`${header}.${forged}.${signature}`, SECRET, ISSUER)).toThrow('Firma inválida');
    expect(() => verifyHs256(token({ exp: now - 1 }), SECRET, ISSUER)).toThrow('Token expirado');
    expect(() => verifyHs256(token({ iss: 'otro' }), SECRET, ISSUER)).toThrow('Emisor no confiable');
    expect(() => verifyHs256('a.b', SECRET, ISSUER)).toThrow('Token mal formado');
  });

  it('rechaza el algoritmo none', () => {
    const header = Buffer.from(JSON.stringify({ alg: 'none' })).toString('base64url');
    const payload = Buffer.from(JSON.stringify({ iss: ISSUER, sub: 'u', exp: now + 60 })).toString('base64url');
    expect(() => verifyHs256(`${header}.${payload}.`, SECRET, ISSUER)).toThrow('Algoritmo no permitido');
  });
});

describe('resolvePrincipal', () => {
  it('asigna rol clínico por defecto según el rol del ERP', () => {
    expect(resolvePrincipal({ sub: 'a', role: 'ADMIN' }).clinicalRole).toBe('MEDICO');
    expect(resolvePrincipal({ sub: 'b', role: 'AUDITOR' }).clinicalRole).toBe('ENFERMERO');
    expect(resolvePrincipal({ sub: 'c', role: 'AGENT' }).clinicalRole).toBe('RECEPCION');
    expect(resolvePrincipal({ sub: 'c', role: 'AGENT' }).sedeId).toBe('SEDE-MIA');
  });

  it('solo el administrador puede elegir otro rol clínico y las sedes se validan', () => {
    expect(resolvePrincipal({ sub: 'a', role: 'ADMIN' }, 'RECEPCION', 'SEDE-TPA')).toMatchObject({ clinicalRole: 'RECEPCION', sedeId: 'SEDE-TPA' });
    expect(() => resolvePrincipal({ sub: 'c', role: 'AGENT' }, 'MEDICO')).toThrow(/solo puede conectarse como RECEPCION/);
    expect(() => resolvePrincipal({ sub: 'a', role: 'ADMIN' }, 'CIRUJANO')).toThrow(/Rol clínico desconocido/);
    expect(() => resolvePrincipal({ sub: 'a', role: 'ADMIN' }, null, 'SEDE-XX')).toThrow(/Sede desconocida/);
  });
});

describe('extractToken / authenticate', () => {
  it('lee el token del header Authorization, del subprotocolo WebSocket o del query string', () => {
    const url = new URL('http://gw/ws?access_token=q');
    expect(extractToken(request({ authorization: 'Bearer h' }), url)).toBe('h');
    expect(extractToken(request({ 'sec-websocket-protocol': 'bearer, s' }), url)).toBe('s');
    expect(extractToken(request(), url)).toBe('q');
    expect(extractToken(request(), new URL('http://gw/ws'))).toBeNull();
  });

  it('devuelve 401 sin token o con token inválido', () => {
    const url = new URL('http://gw/ws?clinicalRole=MEDICO');
    expect(() => authenticate(request(), url, SECRET, ISSUER)).toThrow('Falta el token de acceso');
    expect(() => authenticate(request({ authorization: 'Bearer x.y.z' }), url, SECRET, ISSUER)).toThrow();
    expect(authenticate(request({ authorization: `Bearer ${token()}` }), url, SECRET, ISSUER).clinicalRole).toBe('MEDICO');
  });
});
