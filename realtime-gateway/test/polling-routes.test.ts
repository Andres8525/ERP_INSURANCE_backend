import { afterEach, describe, expect, it } from 'vitest';
import { Router } from '../src/http/router.js';
import { LabJobStore } from '../src/polling/lab-jobs.js';
import { registerPollingRoutes } from '../src/polling/routes.js';
import { listen, testConfig, tokenFor } from './support.js';

let close: (() => Promise<void>) | null = null;
afterEach(async () => {
  await close?.();
  close = null;
});

async function setup() {
  let now = 5_000_000;
  const config = testConfig();
  const router = new Router(['*']);
  registerPollingRoutes(router, config, new LabJobStore(config.pollingIntervalMs, () => now));
  const server = await listen(router);
  close = server.close;
  return { base: server.base, advance: (ms: number) => (now += ms) };
}

describe('short polling /api/realtime/lab-status-polling', () => {
  it('crea el trabajo, devuelve 304 sin cambios y 200 cuando avanza', async () => {
    const { base, advance } = await setup();
    const auth = { Authorization: `Bearer ${tokenFor('u-1')}` };

    const created = await fetch(`${base}/api/realtime/lab-jobs`, { method: 'POST', headers: auth, body: JSON.stringify({ kind: 'RIPS' }) });
    expect(created.status).toBe(202);
    const job = (await created.json()) as { jobId: string; state: string };
    expect(job.state).toBe('QUEUED');
    expect(created.headers.get('location')).toBe(`/api/realtime/lab-status-polling?jobId=${job.jobId}`);
    expect(created.headers.get('x-poll-interval')).toBe('3000');

    const first = await fetch(`${base}/api/realtime/lab-status-polling?jobId=${job.jobId}`, { headers: auth });
    expect(first.status).toBe(200);
    expect(first.headers.get('cache-control')).toBe('no-store');
    const etag = first.headers.get('etag')!;

    const unchanged = await fetch(`${base}/api/realtime/lab-status-polling?jobId=${job.jobId}`, { headers: { ...auth, 'If-None-Match': etag } });
    expect(unchanged.status).toBe(304);

    advance(40_000);
    const done = await fetch(`${base}/api/realtime/lab-status-polling?jobId=${job.jobId}`, { headers: { ...auth, 'If-None-Match': etag } });
    expect(done.status).toBe(200);
    expect(done.headers.get('x-poll-interval')).toBe('0');
    const body = (await done.json()) as { state: string; polls: number; notModified: number; result: { items: string[] } };
    expect(body).toMatchObject({ state: 'COMPLETED', polls: 3, notModified: 1 });
    expect(body.result.items[0]).toContain('AF');
  });

  it('exige token, valida el tipo y oculta trabajos de otros usuarios', async () => {
    const { base } = await setup();
    expect((await fetch(`${base}/api/realtime/lab-jobs`, { method: 'POST' })).status).toBe(401);

    const auth = { Authorization: `Bearer ${tokenFor('u-1')}` };
    expect((await fetch(`${base}/api/realtime/lab-jobs`, { method: 'POST', headers: auth, body: '{"kind":"MRI"}' })).status).toBe(400);
    expect((await fetch(`${base}/api/realtime/lab-status-polling`, { headers: auth })).status).toBe(400);

    const created = (await (await fetch(`${base}/api/realtime/lab-jobs`, { method: 'POST', headers: auth })).json()) as { jobId: string; kind: string };
    expect(created.kind).toBe('LAB_RESULTS');
    const other = await fetch(`${base}/api/realtime/lab-status-polling?jobId=${created.jobId}`, { headers: { Authorization: `Bearer ${tokenFor('u-2')}` } });
    expect(other.status).toBe(404);
  });
});
