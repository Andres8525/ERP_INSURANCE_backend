import type { IncomingMessage } from 'node:http';
import { verifyHs256, type JwtClaims } from './jwt.js';

export const CLINICAL_ROLES = ['MEDICO', 'ENFERMERO', 'RECEPCION'] as const;
export type ClinicalRole = (typeof CLINICAL_ROLES)[number];

export const SEDES = ['SEDE-MIA', 'SEDE-ORL', 'SEDE-TPA'] as const;
export type SedeId = (typeof SEDES)[number];

export interface Principal {
  userId: string;
  email: string;
  systemRole: string;
  clinicalRole: ClinicalRole;
  sedeId: SedeId;
}

const DEFAULT_CLINICAL_ROLE: Record<string, ClinicalRole> = {
  ADMIN: 'MEDICO',
  AUDITOR: 'ENFERMERO',
  AGENT: 'RECEPCION',
};

export class AuthError extends Error {
  constructor(
    message: string,
    readonly status: 401 | 403,
  ) {
    super(message);
    this.name = 'AuthError';
  }
}

function isClinicalRole(value: string | null | undefined): value is ClinicalRole {
  return CLINICAL_ROLES.includes(value as ClinicalRole);
}

function isSede(value: string | null | undefined): value is SedeId {
  return SEDES.includes(value as SedeId);
}

export function resolvePrincipal(claims: JwtClaims, requestedRole?: string | null, requestedSede?: string | null): Principal {
  const systemRole = claims.role ?? 'AGENT';
  const defaultRole = DEFAULT_CLINICAL_ROLE[systemRole] ?? 'RECEPCION';
  let clinicalRole = defaultRole;
  if (requestedRole) {
    if (!isClinicalRole(requestedRole)) throw new AuthError(`Rol clínico desconocido: ${requestedRole}`, 403);
    if (systemRole !== 'ADMIN' && requestedRole !== defaultRole) {
      throw new AuthError(`El rol ${systemRole} solo puede conectarse como ${defaultRole}`, 403);
    }
    clinicalRole = requestedRole;
  }
  if (requestedSede && !isSede(requestedSede)) throw new AuthError(`Sede desconocida: ${requestedSede}`, 403);
  return {
    userId: claims.sub,
    email: claims.email ?? claims.sub,
    systemRole,
    clinicalRole,
    sedeId: requestedSede && isSede(requestedSede) ? requestedSede : 'SEDE-MIA',
  };
}

export function extractToken(request: IncomingMessage, url: URL): string | null {
  const header = request.headers.authorization;
  if (header?.startsWith('Bearer ')) return header.slice(7).trim();
  const protocols = request.headers['sec-websocket-protocol'];
  if (protocols) {
    const values = protocols.split(',').map((value) => value.trim());
    const index = values.indexOf('bearer');
    if (index >= 0 && values[index + 1]) return values[index + 1] ?? null;
  }
  return url.searchParams.get('access_token');
}

export function authenticate(request: IncomingMessage, url: URL, secret: string, issuer: string): Principal {
  const token = extractToken(request, url);
  if (!token) throw new AuthError('Falta el token de acceso', 401);
  let claims: JwtClaims;
  try {
    claims = verifyHs256(token, secret, issuer);
  } catch (error) {
    throw new AuthError((error as Error).message, 401);
  }
  return resolvePrincipal(claims, url.searchParams.get('clinicalRole'), url.searchParams.get('sede'));
}
