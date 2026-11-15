import { describe, expect, it } from 'vitest';
import { ConnectionRegistry } from '../src/ws/connection-registry.js';
import { Heartbeat } from '../src/ws/heartbeat.js';
import { FakeSocket, principal } from './fakes.js';

describe('Heartbeat ping/pong', () => {
  it('envía ping, mantiene a quien responde pong y termina a los zombis liberando el registro', () => {
    const registry = new ConnectionRegistry();
    const dead: string[] = [];
    const heartbeat = new Heartbeat(registry, 30_000, (entry) => dead.push(entry.principal.userId));
    const alive = registry.add(new FakeSocket(), principal('activo'));
    const zombie = registry.add(new FakeSocket(), principal('zombi'));

    expect(heartbeat.sweep()).toEqual({ pinged: 2, terminated: 0 });
    expect((alive.socket as FakeSocket).pings).toBe(1);

    registry.touch(alive.connectionId);
    expect(heartbeat.sweep()).toEqual({ pinged: 1, terminated: 1 });
    expect((zombie.socket as FakeSocket).terminated).toBe(true);
    expect(registry.get(zombie.connectionId)).toBeUndefined();
    expect(registry.isOnline('zombi')).toBe(false);
    expect(dead).toEqual(['zombi']);
    expect(heartbeat.terminated).toBe(1);
  });

  it('termina el socket si el ping lanza un error', () => {
    const registry = new ConnectionRegistry();
    const socket = new FakeSocket();
    socket.ping = () => {
      throw new Error('EPIPE');
    };
    registry.add(socket, principal('roto'));
    const heartbeat = new Heartbeat(registry, 30_000, () => undefined);
    expect(heartbeat.sweep()).toEqual({ pinged: 0, terminated: 1 });
    expect(registry.size).toBe(0);
  });

  it('arranca y se detiene sin dejar temporizadores', () => {
    const heartbeat = new Heartbeat(new ConnectionRegistry(), 10, () => undefined);
    heartbeat.start();
    heartbeat.start();
    heartbeat.stop();
    heartbeat.stop();
    expect(heartbeat.terminated).toBe(0);
  });
});
