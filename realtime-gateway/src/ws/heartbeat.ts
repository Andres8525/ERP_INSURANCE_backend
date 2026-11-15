import type { AuthenticatedSocket, ConnectionRegistry } from './connection-registry.js';

export interface SweepResult {
  pinged: number;
  terminated: number;
}

export class Heartbeat {
  private timer: NodeJS.Timeout | null = null;
  private terminatedTotal = 0;

  constructor(
    private readonly registry: ConnectionRegistry,
    private readonly intervalMs: number,
    private readonly onDead: (entry: AuthenticatedSocket) => void,
  ) {}

  sweep(): SweepResult {
    let pinged = 0;
    let terminated = 0;
    for (const entry of [...this.registry.values()]) {
      if (!entry.isAlive) {
        entry.socket.terminate();
        this.registry.remove(entry.connectionId);
        this.onDead(entry);
        terminated += 1;
        continue;
      }
      entry.isAlive = false;
      try {
        entry.socket.ping();
        pinged += 1;
      } catch {
        entry.socket.terminate();
        this.registry.remove(entry.connectionId);
        this.onDead(entry);
        terminated += 1;
      }
    }
    this.terminatedTotal += terminated;
    return { pinged, terminated };
  }

  start(): void {
    if (this.timer) return;
    this.timer = setInterval(() => this.sweep(), this.intervalMs);
    this.timer.unref();
  }

  stop(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }

  get terminated(): number {
    return this.terminatedTotal;
  }
}
