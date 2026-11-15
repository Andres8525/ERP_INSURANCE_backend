import { Redis, type RedisOptions } from 'ioredis';
import { HUB_CHANNEL, isEnvelope, type Backplane, type BackplaneEnvelope, type EnvelopeHandler } from './backplane.js';

export interface PubSubClient {
  status: string;
  publish(channel: string, message: string): Promise<number>;
  subscribe(channel: string): Promise<unknown>;
  on(event: 'message', listener: (channel: string, message: string) => void): unknown;
  quit(): Promise<unknown>;
}

export class RedisBackplane implements Backplane {
  readonly kind = 'redis' as const;
  readonly fallbackReason = null;
  private readonly handlers = new Set<EnvelopeHandler>();
  private subscribed = false;

  constructor(
    private readonly publisher: PubSubClient,
    private readonly subscriber: PubSubClient,
  ) {
    this.subscriber.on('message', (channel, raw) => {
      if (channel !== HUB_CHANNEL) return;
      let parsed: unknown;
      try {
        parsed = JSON.parse(raw);
      } catch {
        return;
      }
      if (!isEnvelope(parsed)) return;
      for (const handler of this.handlers) handler(parsed);
    });
  }

  async publish(message: BackplaneEnvelope): Promise<void> {
    await this.publisher.publish(HUB_CHANNEL, JSON.stringify(message));
  }

  async subscribe(handler: EnvelopeHandler): Promise<() => void> {
    if (!this.subscribed) {
      await this.subscriber.subscribe(HUB_CHANNEL);
      this.subscribed = true;
    }
    this.handlers.add(handler);
    return () => this.handlers.delete(handler);
  }

  healthy(): boolean {
    return this.publisher.status === 'ready' && this.subscriber.status === 'ready';
  }

  async close(): Promise<void> {
    this.handlers.clear();
    await Promise.allSettled([this.publisher.quit(), this.subscriber.quit()]);
  }
}

function withTimeout<T>(promise: Promise<T>, timeoutMs: number, message: string): Promise<T> {
  let timer: NodeJS.Timeout | undefined;
  const timeout = new Promise<never>((_, reject) => {
    timer = setTimeout(() => reject(new Error(message)), timeoutMs);
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}

export async function connectRedis(url: string, timeoutMs: number): Promise<{ publisher: Redis; subscriber: Redis }> {
  const options: RedisOptions = {
    lazyConnect: true,
    connectTimeout: timeoutMs,
    maxRetriesPerRequest: 2,
    enableOfflineQueue: false,
    retryStrategy: (attempt) => Math.min(attempt * 250, 3000),
  };
  const publisher = new Redis(url, options);
  const subscriber = new Redis(url, options);
  let reported = false;
  const report = (error: Error) => {
    if (reported) return;
    reported = true;
    console.warn(`[backplane] Redis no disponible: ${error.message}`);
  };
  publisher.on('error', report);
  subscriber.on('error', report);
  try {
    await withTimeout(Promise.all([publisher.connect(), subscriber.connect()]), timeoutMs, `Redis no respondió en ${timeoutMs} ms`);
    return { publisher, subscriber };
  } catch (error) {
    publisher.disconnect();
    subscriber.disconnect();
    throw error;
  }
}
