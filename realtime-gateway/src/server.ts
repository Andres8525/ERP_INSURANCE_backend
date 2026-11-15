import { createServer, type Server } from 'node:http';
import type { Backplane } from './backplane/backplane.js';
import { createBackplane } from './backplane/factory.js';
import type { GatewayConfig } from './config.js';
import { Router } from './http/router.js';
import { EventLoopMonitor } from './metrics/event-loop.js';
import { registerMetricsRoutes } from './metrics/routes.js';
import { LabJobStore } from './polling/lab-jobs.js';
import { registerPollingRoutes } from './polling/routes.js';
import { registerSseRoutes } from './sse/routes.js';
import { SseHub } from './sse/sse-hub.js';
import { MedicalHub } from './ws/medical-hub.js';
import { MemoryRecordStore, RedisRecordStore, type RecordStore } from './ws/record-store.js';

export interface Gateway {
  server: Server;
  hub: MedicalHub;
  sse: SseHub;
  backplane: Backplane;
  records: RecordStore;
  listen(port?: number, host?: string): Promise<number>;
  close(): Promise<void>;
}

export interface GatewayOverrides {
  backplane?: Backplane;
  records?: RecordStore;
}

export async function createGateway(config: GatewayConfig, overrides: GatewayOverrides = {}): Promise<Gateway> {
  let redis = null;
  let backplane = overrides.backplane;
  if (!backplane) {
    const setup = await createBackplane(config.redisUrl, config.redisConnectTimeoutMs);
    backplane = setup.backplane;
    redis = setup.redis;
  }
  const records = overrides.records ?? (redis ? new RedisRecordStore(redis) : new MemoryRecordStore());
  const jobs = new LabJobStore(config.pollingIntervalMs);
  const sse = new SseHub({ vitalsIntervalMs: config.vitalsIntervalMs, heartbeatMs: config.sseHeartbeatMs, replayBuffer: config.sseReplayBuffer });
  const hub = new MedicalHub(config, backplane, records);
  const eventLoop = new EventLoopMonitor();
  await hub.start();

  const router = new Router(config.allowedOrigins);
  registerMetricsRoutes(router, { config, backplane, hub, sse, jobs, eventLoop, startedAt: Date.now() });
  registerPollingRoutes(router, config, jobs);
  registerSseRoutes(router, config, sse);

  const server = createServer((request, response) => void router.handle(request, response));
  server.on('upgrade', (request, socket, head) => hub.handleUpgrade(request, socket, head));
  server.keepAliveTimeout = 65_000;
  server.headersTimeout = 66_000;
  server.requestTimeout = 0;

  const activeBackplane = backplane;
  return {
    server,
    hub,
    sse,
    backplane: activeBackplane,
    records,
    listen: (port = config.port, host = '0.0.0.0') =>
      new Promise<number>((resolve) => {
        server.listen(port, host, () => {
          const address = server.address();
          resolve(typeof address === 'object' && address ? address.port : port);
        });
      }),
    close: async () => {
      sse.close();
      await hub.close();
      eventLoop.stop();
      server.closeAllConnections();
      await new Promise<void>((resolve) => server.close(() => resolve()));
      if (!overrides.backplane) await activeBackplane.close();
    },
  };
}
