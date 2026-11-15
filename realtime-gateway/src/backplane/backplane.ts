import { randomUUID } from 'node:crypto';

export type DeliveryTarget =
  | { kind: 'user'; userId: string }
  | { kind: 'room'; roomId: string }
  | { kind: 'sede'; sedeId: string }
  | { kind: 'role'; role: string }
  | { kind: 'all' };

export interface BackplaneEnvelope<T = unknown> {
  id: string;
  origin: string;
  target: DeliveryTarget;
  event: string;
  payload: T;
  publishedAt: number;
}

export type EnvelopeHandler = (envelope: BackplaneEnvelope) => void;

export interface Backplane {
  readonly kind: 'redis' | 'memory';
  readonly fallbackReason: string | null;
  publish(envelope: BackplaneEnvelope): Promise<void>;
  subscribe(handler: EnvelopeHandler): Promise<() => void>;
  healthy(): boolean;
  close(): Promise<void>;
}

export const HUB_CHANNEL = 'erp:medical-hub:events';

export function envelope<T>(origin: string, target: DeliveryTarget, event: string, payload: T, now = Date.now()): BackplaneEnvelope<T> {
  return { id: randomUUID(), origin, target, event, payload, publishedAt: now };
}

export function isEnvelope(value: unknown): value is BackplaneEnvelope {
  if (!value || typeof value !== 'object') return false;
  const candidate = value as Partial<BackplaneEnvelope>;
  return typeof candidate.id === 'string' && typeof candidate.origin === 'string' && typeof candidate.event === 'string'
    && typeof candidate.target === 'object' && candidate.target !== null && typeof candidate.publishedAt === 'number';
}
