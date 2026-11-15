import type { Redis } from 'ioredis';
import type { Backplane } from './backplane.js';
import { InMemoryBackplane } from './memory.js';
import { RedisBackplane, connectRedis } from './redis.js';

export interface BackplaneSetup {
  backplane: Backplane;
  redis: Redis | null;
}

export async function createBackplane(redisUrl: string | null, timeoutMs: number): Promise<BackplaneSetup> {
  if (!redisUrl) {
    return { backplane: new InMemoryBackplane(undefined, 'REDIS_URL no configurado: modo de una sola instancia'), redis: null };
  }
  try {
    const { publisher, subscriber } = await connectRedis(redisUrl, timeoutMs);
    return { backplane: new RedisBackplane(publisher, subscriber), redis: publisher };
  } catch (error) {
    const reason = `Redis inaccesible (${(error as Error).message}): fallback en memoria, sin escalado horizontal`;
    console.warn(`[backplane] ${reason}`);
    return { backplane: new InMemoryBackplane(undefined, reason), redis: null };
  }
}
