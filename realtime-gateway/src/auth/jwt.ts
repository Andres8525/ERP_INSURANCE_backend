import { createHmac, timingSafeEqual } from 'node:crypto';

export interface JwtClaims {
  sub: string;
  email?: string;
  role?: string;
  iss?: string;
  exp?: number;
  iat?: number;
}

export class JwtError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'JwtError';
  }
}

function decodeSegment(segment: string): unknown {
  try {
    return JSON.parse(Buffer.from(segment, 'base64url').toString('utf8'));
  } catch {
    throw new JwtError('Token mal formado');
  }
}

export function signHs256(claims: Record<string, unknown>, secret: string): string {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const payload = Buffer.from(JSON.stringify(claims)).toString('base64url');
  const signature = createHmac('sha256', Buffer.from(secret, 'utf8')).update(`${header}.${payload}`).digest('base64url');
  return `${header}.${payload}.${signature}`;
}

export function verifyHs256(token: string, secret: string, issuer: string, nowSeconds = Math.floor(Date.now() / 1000)): JwtClaims {
  const parts = token.split('.');
  if (parts.length !== 3) throw new JwtError('Token mal formado');
  const [headerPart, payloadPart, signaturePart] = parts as [string, string, string];

  const header = decodeSegment(headerPart) as { alg?: string };
  if (header.alg !== 'HS256') throw new JwtError('Algoritmo no permitido');

  const expected = createHmac('sha256', Buffer.from(secret, 'utf8')).update(`${headerPart}.${payloadPart}`).digest();
  const received = Buffer.from(signaturePart, 'base64url');
  if (received.length !== expected.length || !timingSafeEqual(received, expected)) {
    throw new JwtError('Firma inválida');
  }

  const claims = decodeSegment(payloadPart) as JwtClaims;
  if (typeof claims.sub !== 'string' || claims.sub.length === 0) throw new JwtError('Token sin sujeto');
  if (claims.iss !== issuer) throw new JwtError('Emisor no confiable');
  if (typeof claims.exp !== 'number' || claims.exp <= nowSeconds) throw new JwtError('Token expirado');
  return claims;
}
