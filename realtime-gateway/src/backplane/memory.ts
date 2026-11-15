import { EventEmitter } from 'node:events';
import { HUB_CHANNEL, isEnvelope, type Backplane, type BackplaneEnvelope, type EnvelopeHandler } from './backplane.js';

export class InMemoryBackplane implements Backplane {
  readonly kind = 'memory' as const;
  private readonly listeners = new Set<(raw: string) => void>();
  private closed = false;

  constructor(
    private readonly bus: EventEmitter = new EventEmitter(),
    readonly fallbackReason: string | null = null,
  ) {
    this.bus.setMaxListeners(0);
  }

  async publish(message: BackplaneEnvelope): Promise<void> {
    if (this.closed) throw new Error('Backplane cerrado');
    const raw = JSON.stringify(message);
    setImmediate(() => this.bus.emit(HUB_CHANNEL, raw));
  }

  async subscribe(handler: EnvelopeHandler): Promise<() => void> {
    const listener = (raw: string) => {
      const parsed: unknown = JSON.parse(raw);
      if (isEnvelope(parsed)) handler(parsed);
    };
    this.bus.on(HUB_CHANNEL, listener);
    this.listeners.add(listener);
    return () => {
      this.bus.off(HUB_CHANNEL, listener);
      this.listeners.delete(listener);
    };
  }

  healthy(): boolean {
    return !this.closed;
  }

  async close(): Promise<void> {
    this.closed = true;
    for (const listener of this.listeners) this.bus.off(HUB_CHANNEL, listener);
    this.listeners.clear();
  }
}
