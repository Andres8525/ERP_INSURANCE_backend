import { OPEN, type SocketLike } from './connection-registry.js';

export interface FanOutOptions {
  batchSize: number;
  maxBufferedBytes: number;
  onBatch?: (batch: number, delivered: number) => void;
}

export interface FanOutReport {
  recipients: number;
  delivered: number;
  skippedClosed: number;
  skippedSlow: number;
  failed: number;
  batches: number;
  bytes: number;
  durationMs: number;
}

export const yieldToEventLoop = (): Promise<void> => new Promise((resolve) => setImmediate(resolve));

export async function fanOut(sockets: ReadonlyArray<{ socket: SocketLike }>, payload: string, options: FanOutOptions): Promise<FanOutReport> {
  const startedAt = performance.now();
  const batchSize = Math.max(1, options.batchSize);
  const bytes = Buffer.byteLength(payload);
  const report: FanOutReport = {
    recipients: sockets.length,
    delivered: 0,
    skippedClosed: 0,
    skippedSlow: 0,
    failed: 0,
    batches: 0,
    bytes: 0,
    durationMs: 0,
  };

  for (let start = 0; start < sockets.length; start += batchSize) {
    const end = Math.min(start + batchSize, sockets.length);
    for (let index = start; index < end; index++) {
      const { socket } = sockets[index]!;
      if (socket.readyState !== OPEN) {
        report.skippedClosed += 1;
        continue;
      }
      if (socket.bufferedAmount > options.maxBufferedBytes) {
        report.skippedSlow += 1;
        continue;
      }
      try {
        socket.send(payload);
        report.delivered += 1;
        report.bytes += bytes;
      } catch {
        report.failed += 1;
      }
    }
    report.batches += 1;
    options.onBatch?.(report.batches, report.delivered);
    if (end < sockets.length) await yieldToEventLoop();
  }

  report.durationMs = Math.round((performance.now() - startedAt) * 100) / 100;
  return report;
}
