import type { IncomingMessage, ServerResponse } from 'node:http';
import { AuthError } from '../auth/principal.js';

export type Handler = (request: IncomingMessage, response: ServerResponse, url: URL) => void | Promise<void>;

interface Route {
  method: string;
  path: string;
  handler: Handler;
}

export class HttpError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'HttpError';
  }
}

const EXPOSED_HEADERS = 'ETag, Retry-After, X-Poll-Interval, X-Instance-Id';

export function applyCors(request: IncomingMessage, response: ServerResponse, allowedOrigins: string[]): void {
  const origin = request.headers.origin;
  if (origin && (allowedOrigins.includes(origin) || allowedOrigins.includes('*'))) {
    response.setHeader('Access-Control-Allow-Origin', origin);
    response.setHeader('Vary', 'Origin');
    response.setHeader('Access-Control-Allow-Credentials', 'true');
    response.setHeader('Access-Control-Expose-Headers', EXPOSED_HEADERS);
  }
  response.setHeader('Cross-Origin-Resource-Policy', 'cross-origin');
  response.setHeader('X-Content-Type-Options', 'nosniff');
}

export function sendJson(response: ServerResponse, status: number, body: unknown, headers: Record<string, string> = {}): void {
  const payload = JSON.stringify(body);
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
    ...headers,
  });
  response.end(payload);
}

export function sendProblem(response: ServerResponse, status: number, detail: string): void {
  if (response.headersSent) {
    response.end();
    return;
  }
  sendJson(response, status, { status, title: status >= 500 ? 'Error interno' : 'Solicitud rechazada', detail }, {
    'Content-Type': 'application/problem+json; charset=utf-8',
  });
}

export async function readJson<T>(request: IncomingMessage, maxBytes = 16 * 1024): Promise<T> {
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of request) {
    size += (chunk as Buffer).length;
    if (size > maxBytes) throw new HttpError(413, 'Cuerpo demasiado grande');
    chunks.push(chunk as Buffer);
  }
  if (size === 0) return {} as T;
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8')) as T;
  } catch {
    throw new HttpError(400, 'JSON inválido');
  }
}

export class Router {
  private readonly routes: Route[] = [];

  constructor(private readonly allowedOrigins: string[]) {}

  get(path: string, handler: Handler): this {
    this.routes.push({ method: 'GET', path, handler });
    return this;
  }

  post(path: string, handler: Handler): this {
    this.routes.push({ method: 'POST', path, handler });
    return this;
  }

  async handle(request: IncomingMessage, response: ServerResponse): Promise<void> {
    const url = new URL(request.url ?? '/', 'http://gateway.local');
    applyCors(request, response, this.allowedOrigins);

    if (request.method === 'OPTIONS') {
      response.writeHead(204, {
        'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
        'Access-Control-Allow-Headers': 'Authorization, Content-Type, If-None-Match, Last-Event-ID',
        'Access-Control-Max-Age': '600',
      });
      response.end();
      return;
    }

    const candidates = this.routes.filter((route) => route.path === url.pathname);
    const route = candidates.find((candidate) => candidate.method === request.method);
    if (!route) {
      if (candidates.length > 0) {
        response.setHeader('Allow', candidates.map((candidate) => candidate.method).join(', '));
        sendProblem(response, 405, 'Método no permitido');
      } else {
        sendProblem(response, 404, 'Ruta no encontrada');
      }
      return;
    }

    try {
      await route.handler(request, response, url);
    } catch (error) {
      if (error instanceof AuthError || error instanceof HttpError) {
        sendProblem(response, error.status, error.message);
      } else {
        console.error('[http] error no controlado', error);
        sendProblem(response, 500, 'Error interno del gateway');
      }
    }
  }
}
