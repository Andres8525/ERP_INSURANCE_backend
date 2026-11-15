import { EventEmitter } from 'node:events';
import { describe, expect, it } from 'vitest';
import { HUB_CHANNEL, envelope, type BackplaneEnvelope } from '../src/backplane/backplane.js';
import { createBackplane } from '../src/backplane/factory.js';
import { RedisBackplane, type PubSubClient } from '../src/backplane/redis.js';

class FakeRedisServer extends EventEmitter {}

class FakeRedisClient extends EventEmitter implements PubSubClient {
  status = 'ready';
  readonly channels = new Set<string>();

  constructor(private readonly server: FakeRedisServer) {
    super();
    server.on('publish', (channel: string, message: string) => {
      if (this.channels.has(channel)) this.emit('message', channel, message);
    });
  }

  async publish(channel: string, message: string): Promise<number> {
    this.server.emit('publish', channel, message);
    return 1;
  }

  async subscribe(channel: string): Promise<unknown> {
    this.channels.add(channel);
    return 1;
  }

  async quit(): Promise<unknown> {
    this.status = 'end';
    return 'OK';
  }
}

function instance(server: FakeRedisServer) {
  const publisher = new FakeRedisClient(server);
  const subscriber = new FakeRedisClient(server);
  return { backplane: new RedisBackplane(publisher, subscriber), publisher, subscriber };
}

describe('RedisBackplane', () => {
  it('publica en el canal compartido y entrega a las réplicas suscritas', async () => {
    const server = new FakeRedisServer();
    const a = instance(server);
    const b = instance(server);
    const received: BackplaneEnvelope[] = [];
    await b.backplane.subscribe((message) => received.push(message));
    expect(b.subscriber.channels.has(HUB_CHANNEL)).toBe(true);

    await a.backplane.publish(envelope('gw-a', { kind: 'room', roomId: 'tele-1' }, 'chat.message', { text: 'Hola' }));
    expect(received).toHaveLength(1);
    expect(received[0]).toMatchObject({ origin: 'gw-a', event: 'chat.message', payload: { text: 'Hola' } });
    expect(a.backplane.healthy()).toBe(true);
  });

  it('ignora mensajes de otros canales, JSON inválido o sobres mal formados', async () => {
    const server = new FakeRedisServer();
    const { backplane, subscriber } = instance(server);
    const received: unknown[] = [];
    await backplane.subscribe((message) => received.push(message));
    subscriber.emit('message', 'otro-canal', JSON.stringify(envelope('x', { kind: 'all' }, 'e', 1)));
    subscriber.emit('message', HUB_CHANNEL, '{roto');
    subscriber.emit('message', HUB_CHANNEL, JSON.stringify({ hola: 1 }));
    expect(received).toHaveLength(0);
  });

  it('cierra ambas conexiones', async () => {
    const { backplane, publisher, subscriber } = instance(new FakeRedisServer());
    await backplane.close();
    expect([publisher.status, subscriber.status]).toEqual(['end', 'end']);
    expect(backplane.healthy()).toBe(false);
  });
});

describe('createBackplane', () => {
  it('usa memoria cuando no hay REDIS_URL', async () => {
    const { backplane, redis } = await createBackplane(null, 500);
    expect(backplane.kind).toBe('memory');
    expect(backplane.fallbackReason).toContain('REDIS_URL no configurado');
    expect(redis).toBeNull();
    await backplane.close();
  });

  it('cae a memoria si Redis no responde, sin tumbar el gateway', async () => {
    const { backplane, redis } = await createBackplane('redis://127.0.0.1:1', 400);
    expect(backplane.kind).toBe('memory');
    expect(backplane.fallbackReason).toContain('fallback en memoria');
    expect(redis).toBeNull();
    await backplane.close();
  });
});
