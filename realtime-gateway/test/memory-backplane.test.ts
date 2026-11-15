import { EventEmitter } from 'node:events';
import { describe, expect, it } from 'vitest';
import { envelope, isEnvelope, type BackplaneEnvelope } from '../src/backplane/backplane.js';
import { InMemoryBackplane } from '../src/backplane/memory.js';
import { wait } from './support.js';

describe('InMemoryBackplane', () => {
  it('entrega los mensajes a todas las instancias que comparten el bus, de forma asíncrona y serializada', async () => {
    const bus = new EventEmitter();
    const serverA = new InMemoryBackplane(bus);
    const serverB = new InMemoryBackplane(bus);
    const receivedA: BackplaneEnvelope[] = [];
    const receivedB: BackplaneEnvelope[] = [];
    await serverA.subscribe((message) => receivedA.push(message));
    await serverB.subscribe((message) => receivedB.push(message));

    const payload = { text: 'Interconsulta cardiología', at: new Date('2026-11-15T10:00:00Z') };
    await serverA.publish(envelope('gw-a', { kind: 'user', userId: 'medico-2' }, 'consult.request', payload));
    expect(receivedB).toHaveLength(0);
    await wait(5);

    expect(receivedA).toHaveLength(1);
    expect(receivedB).toHaveLength(1);
    expect(receivedB[0]).toMatchObject({ origin: 'gw-a', target: { kind: 'user', userId: 'medico-2' }, event: 'consult.request' });
    expect((receivedB[0]!.payload as { at: string }).at).toBe('2026-11-15T10:00:00.000Z');
  });

  it('deja de entregar al desuscribirse o cerrar y rechaza publicar cerrado', async () => {
    const backplane = new InMemoryBackplane();
    const received: unknown[] = [];
    const unsubscribe = await backplane.subscribe((message) => received.push(message));
    unsubscribe();
    await backplane.publish(envelope('gw', { kind: 'all' }, 'turns.called', {}));
    await wait(5);
    expect(received).toHaveLength(0);

    await backplane.close();
    expect(backplane.healthy()).toBe(false);
    await expect(backplane.publish(envelope('gw', { kind: 'all' }, 'x', {}))).rejects.toThrow('cerrado');
  });

  it('valida la forma del sobre', () => {
    expect(isEnvelope(envelope('gw', { kind: 'all' }, 'x', 1))).toBe(true);
    expect(isEnvelope({ id: 'x' })).toBe(false);
    expect(isEnvelope(null)).toBe(false);
  });
});
