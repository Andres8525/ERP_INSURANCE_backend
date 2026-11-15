import { authenticate, type SedeId } from '../auth/principal.js';
import type { Backplane } from '../backplane/backplane.js';
import type { GatewayConfig } from '../config.js';
import { HttpError, sendJson, type Router } from '../http/router.js';
import type { LabJobStore } from '../polling/lab-jobs.js';
import type { SseHub } from '../sse/sse-hub.js';
import type { SocketLike } from '../ws/connection-registry.js';
import type { MedicalHub } from '../ws/medical-hub.js';
import type { EventLoopMonitor } from './event-loop.js';

const MAX_VIRTUAL_TERMINALS = 20_000;
const SEDES: SedeId[] = ['SEDE-MIA', 'SEDE-ORL', 'SEDE-TPA'];

class VirtualTerminal implements SocketLike {
  readyState = 1;
  bufferedAmount = 0;
  received = 0;

  send(): void {
    this.received += 1;
  }

  ping(): void {}

  terminate(): void {
    this.readyState = 3;
  }

  close(): void {
    this.readyState = 3;
  }
}

export interface MetricsDeps {
  config: GatewayConfig;
  backplane: Backplane;
  hub: MedicalHub;
  sse: SseHub;
  jobs: LabJobStore;
  eventLoop: EventLoopMonitor;
  startedAt: number;
}

export function registerMetricsRoutes(router: Router, deps: MetricsDeps): void {
  const virtual = new Set<string>();

  router.get('/health', (_request, response) => {
    sendJson(response, deps.backplane.healthy() ? 200 : 503, {
      status: deps.backplane.healthy() ? 'UP' : 'DEGRADED',
      instanceId: deps.config.instanceId,
      backplane: deps.backplane.kind,
    });
  });

  router.get('/api/realtime/metrics', (request, response, url) => {
    authenticate(request, url, deps.config.jwtSecret, deps.config.jwtIssuer);
    const memory = process.memoryUsage();
    sendJson(response, 200, {
      instanceId: deps.config.instanceId,
      uptimeSeconds: Math.round((Date.now() - deps.startedAt) / 1000),
      backplane: { kind: deps.backplane.kind, healthy: deps.backplane.healthy(), fallbackReason: deps.backplane.fallbackReason },
      websocket: { ...deps.hub.registry.snapshot(), virtualTerminals: virtual.size, ...deps.hub.stats() },
      sse: deps.sse.stats(),
      polling: { activeJobs: deps.jobs.size(), recommendedIntervalMs: deps.config.pollingIntervalMs },
      eventLoop: deps.eventLoop.snapshot(),
      memory: { rssMb: Math.round(memory.rss / 1_048_576), heapUsedMb: Math.round(memory.heapUsed / 1_048_576) },
    }, { 'Cache-Control': 'no-store' });
  });

  router.post('/api/realtime/load-test/terminals', (request, response, url) => {
    const user = authenticate(request, url, deps.config.jwtSecret, deps.config.jwtIssuer);
    if (user.systemRole !== 'ADMIN') throw new HttpError(403, 'Solo un administrador puede simular terminales');
    const count = Number.parseInt(url.searchParams.get('count') ?? '1000', 10);
    if (!Number.isInteger(count) || count < 1 || count + virtual.size > MAX_VIRTUAL_TERMINALS) {
      throw new HttpError(400, `count debe estar entre 1 y ${MAX_VIRTUAL_TERMINALS - virtual.size}`);
    }
    for (let index = 0; index < count; index++) {
      const sedeId = SEDES[(virtual.size + index) % SEDES.length]!;
      const entry = deps.hub.registry.add(new VirtualTerminal(), {
        userId: `terminal-${virtual.size + index}`,
        email: `terminal-${virtual.size + index}@sala-espera`,
        systemRole: 'AGENT',
        clinicalRole: 'RECEPCION',
        sedeId,
      });
      virtual.add(entry.connectionId);
    }
    sendJson(response, 201, { virtualTerminals: virtual.size, connections: deps.hub.registry.size });
  });

  router.post('/api/realtime/load-test/reset', (request, response, url) => {
    const user = authenticate(request, url, deps.config.jwtSecret, deps.config.jwtIssuer);
    if (user.systemRole !== 'ADMIN') throw new HttpError(403, 'Solo un administrador puede limpiar terminales');
    for (const id of virtual) deps.hub.registry.remove(id);
    virtual.clear();
    sendJson(response, 200, { virtualTerminals: 0, connections: deps.hub.registry.size });
  });
}
