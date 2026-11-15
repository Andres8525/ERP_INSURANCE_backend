import { describe, expect, it } from 'vitest';
import { EventLoopMonitor } from '../src/metrics/event-loop.js';
import { fanOut } from '../src/ws/fan-out.js';
import { FakeSocket } from './fakes.js';

function terminals(count: number) {
  return Array.from({ length: count }, () => ({ socket: new FakeSocket() }));
}

describe('fan-out por lotes', () => {
  it('entrega una alerta Código Azul a 10.000 terminales en lotes sin acaparar el Event Loop', async () => {
    const targets = terminals(10_000);
    const timeline: string[] = [];
    let ioTicks = 0;
    const io = setInterval(() => {
      ioTicks += 1;
      timeline.push('io');
    }, 0);
    setImmediate(() => timeline.push('immediate'));

    const report = await fanOut(targets, JSON.stringify({ type: 'alert.code-blue', room: 'UCI-03' }), {
      batchSize: 500,
      maxBufferedBytes: 1_000_000,
      onBatch: (batch) => timeline.push(`batch-${batch}`),
    });
    clearInterval(io);

    expect(report).toMatchObject({ recipients: 10_000, delivered: 10_000, batches: 20, skippedClosed: 0, skippedSlow: 0, failed: 0 });
    expect(report.bytes).toBe(10_000 * JSON.stringify({ type: 'alert.code-blue', room: 'UCI-03' }).length);
    expect(timeline.indexOf('immediate')).toBeLessThan(timeline.indexOf('batch-20'));
    expect(timeline.indexOf('immediate')).toBeGreaterThan(timeline.indexOf('batch-1'));
    expect(ioTicks).toBeGreaterThan(0);
    expect(targets.every(({ socket }) => socket.sent.length === 1)).toBe(true);
  });

  it('serializa una sola vez y omite sockets cerrados, lentos o que fallan', async () => {
    const targets = terminals(5);
    targets[0]!.socket.readyState = 3;
    targets[1]!.socket.bufferedAmount = 2_000_000;
    targets[2]!.socket.send = () => {
      throw new Error('ECONNRESET');
    };
    const report = await fanOut(targets, '{"type":"turns.called"}', { batchSize: 2, maxBufferedBytes: 1_000_000 });
    expect(report).toMatchObject({ recipients: 5, delivered: 2, skippedClosed: 1, skippedSlow: 1, failed: 1, batches: 3 });
    expect(targets[3]!.socket.sent[0]).toBe(targets[4]!.socket.sent[0]);
  });

  it('no falla sin destinatarios y mide el retraso del Event Loop', async () => {
    expect(await fanOut([], 'x', { batchSize: 10, maxBufferedBytes: 1 })).toMatchObject({ recipients: 0, batches: 0 });
    const monitor = new EventLoopMonitor(10);
    await new Promise((resolve) => setTimeout(resolve, 30));
    const snapshot = monitor.snapshot(true);
    expect(snapshot.maxMs).toBeGreaterThanOrEqual(0);
    expect(snapshot.p99Ms).toBeGreaterThanOrEqual(0);
    monitor.stop();
  });
});
