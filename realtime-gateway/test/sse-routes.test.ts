import { afterEach, describe, expect, it } from 'vitest';
import { Router } from '../src/http/router.js';
import { SseHub } from '../src/sse/sse-hub.js';
import { parseLastEventId, registerSseRoutes } from '../src/sse/routes.js';
import { listen, testConfig, tokenFor } from './support.js';

let cleanup: Array<() => Promise<void> | void> = [];
afterEach(async () => {
  for (const step of cleanup.reverse()) await step();
  cleanup = [];
});

async function setup() {
  const config = testConfig({ vitalsIntervalMs: 40, sseHeartbeatMs: 30 });
  const hub = new SseHub({ vitalsIntervalMs: 40, heartbeatMs: 30, replayBuffer: 100 });
  const router = new Router(['*']);
  registerSseRoutes(router, config, hub);
  const server = await listen(router);
  cleanup.push(server.close, () => hub.close());
  return { base: server.base, hub };
}

async function readUntil(response: Response, predicate: (text: string) => boolean, timeoutMs = 3000): Promise<string> {
  const reader = response.body!.getReader();
  const decoder = new TextDecoder();
  let text = '';
  const deadline = Date.now() + timeoutMs;
  while (!predicate(text) && Date.now() < deadline) {
    const { value, done } = await reader.read();
    if (done) break;
    text += decoder.decode(value, { stream: true });
  }
  await reader.cancel();
  return text;
}

describe('SSE /api/realtime/vitals-stream', () => {
  it('transmite signos vitales con id de evento y comentarios de heartbeat', async () => {
    const { base } = await setup();
    const controller = new AbortController();
    const response = await fetch(`${base}/api/realtime/vitals-stream?beds=UCI-02&access_token=${tokenFor('u-1', 'AUDITOR')}`, {
      signal: controller.signal,
    });
    expect(response.status).toBe(200);
    expect(response.headers.get('content-type')).toBe('text/event-stream; charset=utf-8');
    expect(response.headers.get('x-instance-id')).toBe('gw-test');

    const text = await readUntil(response, (value) => (value.match(/event: vitals/g) ?? []).length >= 2 && value.includes(': ping'));
    controller.abort();
    expect(text).toMatch(/id: \d+\nevent: vitals\ndata: \{"bedId":"UCI-02"/);
    expect(text).not.toContain('"bedId":"UCI-01"');
    expect(text).toContain(': ping');
  });

  it('rechaza sin token, recepción, camas inexistentes y libera el cliente al desconectarse', async () => {
    const { base, hub } = await setup();
    expect((await fetch(`${base}/api/realtime/vitals-stream`)).status).toBe(401);
    expect((await fetch(`${base}/api/realtime/vitals-stream?access_token=${tokenFor('r', 'AGENT')}`)).status).toBe(403);
    expect((await fetch(`${base}/api/realtime/vitals-stream?beds=UCI-99&access_token=${tokenFor('n', 'AUDITOR')}`)).status).toBe(400);

    const controller = new AbortController();
    const response = await fetch(`${base}/api/realtime/vitals-stream?access_token=${tokenFor('n', 'AUDITOR')}`, { signal: controller.signal });
    await readUntil(response, (value) => value.includes('event: vitals'));
    controller.abort();
    const deadline = Date.now() + 2000;
    while (hub.stats().clients > 0 && Date.now() < deadline) await new Promise((resolve) => setTimeout(resolve, 20));
    expect(hub.stats()).toMatchObject({ clients: 0, ticking: false });
  });

  it('lista las camas y entiende Last-Event-ID por cabecera o query', async () => {
    const { base } = await setup();
    const beds = (await (await fetch(`${base}/api/realtime/icu-beds`, { headers: { Authorization: `Bearer ${tokenFor('n')}` } })).json()) as unknown[];
    expect(beds).toHaveLength(6);
    expect(parseLastEventId('42', null)).toBe(42);
    expect(parseLastEventId(undefined, '7')).toBe(7);
    expect(parseLastEventId(undefined, 'abc')).toBeNull();
    expect(parseLastEventId(undefined, null)).toBeNull();
  });
});
