import { loadConfig } from './config.js';
import { createGateway } from './server.js';

const config = loadConfig();
const gateway = await createGateway(config);
const port = await gateway.listen();

console.log(
  `[realtime-gateway] instancia ${config.instanceId} escuchando en :${port} · backplane ${gateway.backplane.kind}` +
    (gateway.backplane.fallbackReason ? ` (${gateway.backplane.fallbackReason})` : '') +
    ` · historias clínicas ${gateway.records.kind}`,
);

let stopping = false;
async function shutdown(signal: string): Promise<void> {
  if (stopping) return;
  stopping = true;
  console.log(`[realtime-gateway] ${signal}: cerrando conexiones en orden`);
  const force = setTimeout(() => process.exit(1), 10_000);
  force.unref();
  await gateway.close();
  process.exit(0);
}

process.on('SIGTERM', () => void shutdown('SIGTERM'));
process.on('SIGINT', () => void shutdown('SIGINT'));
