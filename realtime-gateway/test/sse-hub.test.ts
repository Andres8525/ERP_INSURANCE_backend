import { EventEmitter } from 'node:events';
import type { ServerResponse } from 'node:http';
import { afterEach, describe, expect, it } from 'vitest';
import type { Principal } from '../src/auth/principal.js';
import { SseHub, formatEvent, matches } from '../src/sse/sse-hub.js';
import { ICU_BEDS } from '../src/sse/vitals.js';

class FakeResponse extends EventEmitter {
  chunks: string[] = [];
  status = 0;
  headers: Record<string, string> = {};
  writableEnded = false;
  backpressure = false;

  writeHead(status: number, headers: Record<string, string>) {
    this.status = status;
    this.headers = headers;
    return this;
  }

  write(chunk: string) {
    this.chunks.push(chunk);
    return !this.backpressure;
  }

  end() {
    this.writableEnded = true;
  }

  text() {
    return this.chunks.join('');
  }
}

const nurse: Principal = { userId: 'n-1', email: 'n@nexo.demo', systemRole: 'AUDITOR', clinicalRole: 'ENFERMERO', sedeId: 'SEDE-MIA' };
let hub: SseHub | null = null;

function create(replayBuffer = 50) {
  hub = new SseHub({ vitalsIntervalMs: 60_000, heartbeatMs: 60_000, replayBuffer, pharmacyEveryTicks: 2 });
  return hub;
}

function asResponse(fake: FakeResponse): ServerResponse {
  return fake as unknown as ServerResponse;
}

afterEach(() => {
  hub?.close();
  hub = null;
});

describe('formato SSE', () => {
  it('serializa id, event y data separados por línea en blanco', () => {
    expect(formatEvent({ id: 7, event: 'vitals', data: '{"a":1}' })).toBe('id: 7\nevent: vitals\ndata: {"a":1}\n\n');
  });

  it('filtra por cama y por sede', () => {
    expect(matches({ bedIds: new Set(['UCI-01']), sedeId: null }, { bedId: 'UCI-01', sedeId: 'SEDE-MIA' })).toBe(true);
    expect(matches({ bedIds: new Set(['UCI-01']), sedeId: null }, { bedId: 'UCI-02', sedeId: 'SEDE-MIA' })).toBe(false);
    expect(matches({ bedIds: null, sedeId: 'SEDE-ORL' }, { bedId: 'UCI-01', sedeId: 'SEDE-MIA' })).toBe(false);
  });
});

describe('SseHub', () => {
  it('abre el stream con cabeceras text/event-stream y solo entrega las camas filtradas', () => {
    const sse = create();
    const response = new FakeResponse();
    sse.connect(asResponse(response), nurse, { bedIds: new Set(['UCI-01']), sedeId: null }, null);

    expect(response.status).toBe(200);
    expect(response.headers['Content-Type']).toBe('text/event-stream; charset=utf-8');
    expect(response.headers['Cache-Control']).toBe('no-cache, no-transform');
    expect(response.text()).toContain('retry: 3000');

    sse.publishTick();
    const text = response.text();
    expect(text).toContain('event: vitals');
    expect(text).toContain('"bedId":"UCI-01"');
    expect(text).not.toContain('"bedId":"UCI-02"');
    expect(sse.stats()).toMatchObject({ clients: 1, eventsPublished: ICU_BEDS.length, eventsDelivered: 1, ticking: true });
  });

  it('reenvía lo perdido con Last-Event-ID y avisa reset si ya salió del búfer', () => {
    const sse = create(8);
    const first = new FakeResponse();
    sse.connect(asResponse(first), nurse, { bedIds: null, sedeId: null }, null);
    sse.publishTick();
    sse.publishTick();
    sse.publishTick();

    const resumed = new FakeResponse();
    sse.connect(asResponse(resumed), nurse, { bedIds: null, sedeId: null }, 14);
    const ids = [...resumed.text().matchAll(/^id: (\d+)$/gm)].map((match) => Number(match[1]));
    expect(ids).toEqual([15, 16, 17, 18, 19]);

    const stale = new FakeResponse();
    sse.connect(asResponse(stale), nurse, { bedIds: null, sedeId: null }, 1);
    expect(stale.text()).toContain('event: reset');
  });

  it('emite avisos de farmacia periódicos', () => {
    const sse = create();
    const response = new FakeResponse();
    sse.connect(asResponse(response), nurse, { bedIds: null, sedeId: null }, null);
    sse.publishTick();
    sse.publishTick();
    sse.publishTick();
    expect(response.text()).toContain('event: pharmacy');
    expect(response.text()).toContain('"noticeId":"FAR-000001"');
  });

  it('descarta eventos para clientes congestionados hasta el evento drain', () => {
    const sse = create();
    const response = new FakeResponse();
    response.backpressure = true;
    sse.connect(asResponse(response), nurse, { bedIds: new Set(['UCI-01']), sedeId: null }, null);
    sse.publishTick();
    sse.publishTick();
    expect(sse.stats().eventsDropped).toBe(1);

    response.backpressure = false;
    response.emit('drain');
    sse.publishTick();
    expect(sse.stats().eventsDelivered).toBe(2);
  });

  it('libera la conexión al cerrarse y detiene el temporizador sin clientes', () => {
    const sse = create();
    const response = new FakeResponse();
    sse.connect(asResponse(response), nurse, { bedIds: null, sedeId: null }, null);
    response.emit('close');
    expect(sse.stats()).toMatchObject({ clients: 0, ticking: false });
    expect(response.writableEnded).toBe(true);
  });
});
