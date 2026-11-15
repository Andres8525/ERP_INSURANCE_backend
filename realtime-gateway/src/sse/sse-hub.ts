import type { ServerResponse } from 'node:http';
import { randomUUID } from 'node:crypto';
import type { Principal } from '../auth/principal.js';
import { ICU_BEDS, pharmacyNotice, sampleVitals, type IcuBed } from './vitals.js';

export interface SseEvent {
  id: number;
  event: 'vitals' | 'pharmacy';
  bedId: string;
  sedeId: string;
  data: string;
}

export interface SseFilter {
  bedIds: Set<string> | null;
  sedeId: string | null;
}

interface SseClient {
  id: string;
  principal: Principal;
  response: ServerResponse;
  filter: SseFilter;
  congested: boolean;
  sent: number;
  dropped: number;
  connectedAt: number;
}

export interface SseHubOptions {
  vitalsIntervalMs: number;
  heartbeatMs: number;
  replayBuffer: number;
  pharmacyEveryTicks?: number;
  beds?: IcuBed[];
}

export interface SseStats {
  clients: number;
  eventsPublished: number;
  eventsDelivered: number;
  eventsDropped: number;
  lastEventId: number;
  ticking: boolean;
}

export function formatEvent(event: Pick<SseEvent, 'id' | 'event' | 'data'>): string {
  return `id: ${event.id}\nevent: ${event.event}\ndata: ${event.data}\n\n`;
}

export function matches(filter: SseFilter, event: Pick<SseEvent, 'bedId' | 'sedeId'>): boolean {
  if (filter.bedIds && !filter.bedIds.has(event.bedId)) return false;
  if (filter.sedeId && filter.sedeId !== event.sedeId) return false;
  return true;
}

export class SseHub {
  private readonly clients = new Map<string, SseClient>();
  private readonly buffer: SseEvent[] = [];
  private readonly beds: IcuBed[];
  private readonly pharmacyEveryTicks: number;
  private ticker: NodeJS.Timeout | null = null;
  private heartbeat: NodeJS.Timeout | null = null;
  private tick = 0;
  private lastId = 0;
  private published = 0;
  private delivered = 0;
  private dropped = 0;

  constructor(private readonly options: SseHubOptions) {
    this.beds = options.beds ?? ICU_BEDS;
    this.pharmacyEveryTicks = options.pharmacyEveryTicks ?? 15;
  }

  connect(response: ServerResponse, principal: Principal, filter: SseFilter, lastEventId: number | null): string {
    const client: SseClient = {
      id: randomUUID(),
      principal,
      response,
      filter,
      congested: false,
      sent: 0,
      dropped: 0,
      connectedAt: Date.now(),
    };
    response.writeHead(200, {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache, no-transform',
      Connection: 'keep-alive',
      'X-Accel-Buffering': 'no',
    });
    response.write(`retry: 3000\n: conectado a ${this.beds.length} camas\n\n`);
    response.on('drain', () => {
      client.congested = false;
    });
    const release = () => this.disconnect(client.id);
    response.on('close', release);
    response.on('error', release);
    this.clients.set(client.id, client);
    this.replay(client, lastEventId);
    this.ensureRunning();
    return client.id;
  }

  disconnect(clientId: string): void {
    const client = this.clients.get(clientId);
    if (!client) return;
    this.clients.delete(clientId);
    if (!client.response.writableEnded) client.response.end();
    if (this.clients.size === 0) this.stop();
  }

  publishTick(now: Date = new Date()): SseEvent[] {
    const events: SseEvent[] = this.beds.map((bed) => {
      const sample = sampleVitals(bed, this.tick, now);
      return this.record('vitals', bed.bedId, bed.sedeId, JSON.stringify(sample));
    });
    if (this.tick > 0 && this.tick % this.pharmacyEveryTicks === 0) {
      const notice = pharmacyNotice(this.tick / this.pharmacyEveryTicks, now);
      events.push(this.record('pharmacy', notice.bedId, notice.sedeId, JSON.stringify(notice)));
    }
    this.tick += 1;
    for (const event of events) this.dispatch(event);
    return events;
  }

  stats(): SseStats {
    return {
      clients: this.clients.size,
      eventsPublished: this.published,
      eventsDelivered: this.delivered,
      eventsDropped: this.dropped,
      lastEventId: this.lastId,
      ticking: this.ticker !== null,
    };
  }

  close(): void {
    for (const id of [...this.clients.keys()]) this.disconnect(id);
    this.stop();
  }

  private record(event: SseEvent['event'], bedId: string, sedeId: string, data: string): SseEvent {
    const entry: SseEvent = { id: ++this.lastId, event, bedId, sedeId, data };
    this.buffer.push(entry);
    if (this.buffer.length > this.options.replayBuffer) this.buffer.shift();
    this.published += 1;
    return entry;
  }

  private replay(client: SseClient, lastEventId: number | null): void {
    if (lastEventId === null) return;
    const oldest = this.buffer[0]?.id ?? this.lastId + 1;
    if (lastEventId < oldest - 1) {
      client.response.write(`event: reset\ndata: ${JSON.stringify({ reason: 'Eventos fuera del búfer de reenvío', oldest })}\n\n`);
      return;
    }
    for (const event of this.buffer) {
      if (event.id > lastEventId) this.write(client, event);
    }
  }

  private dispatch(event: SseEvent): void {
    for (const client of this.clients.values()) {
      if (matches(client.filter, event)) this.write(client, event);
    }
  }

  private write(client: SseClient, event: SseEvent): void {
    if (client.congested) {
      client.dropped += 1;
      this.dropped += 1;
      return;
    }
    const flushed = client.response.write(formatEvent(event));
    client.sent += 1;
    this.delivered += 1;
    if (!flushed) client.congested = true;
  }

  private ensureRunning(): void {
    if (this.ticker) return;
    this.ticker = setInterval(() => this.publishTick(), this.options.vitalsIntervalMs);
    this.heartbeat = setInterval(() => {
      for (const client of this.clients.values()) {
        if (!client.congested) client.response.write(`: ping ${Date.now()}\n\n`);
      }
    }, this.options.heartbeatMs);
    this.ticker.unref();
    this.heartbeat.unref();
  }

  private stop(): void {
    if (this.ticker) clearInterval(this.ticker);
    if (this.heartbeat) clearInterval(this.heartbeat);
    this.ticker = null;
    this.heartbeat = null;
  }
}
