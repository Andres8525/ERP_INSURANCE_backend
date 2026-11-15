import { describe, expect, it } from 'vitest';
import { LabJobLimitReached, LabJobNotFound, LabJobStore } from '../src/polling/lab-jobs.js';

function store() {
  let now = 1_000_000;
  const jobs = new LabJobStore(3000, () => now);
  return { jobs, advance: (ms: number) => (now += ms) };
}

describe('LabJobStore (short polling)', () => {
  it('avanza de cola a procesamiento y a completado según el tiempo transcurrido', () => {
    const { jobs, advance } = store();
    const job = jobs.create('LAB_RESULTS', 'u-1');

    expect(jobs.status(job)).toMatchObject({ state: 'QUEUED', progress: 0, stage: 'En cola', recommendedIntervalMs: 3000 });

    advance(1500 + 9000);
    const halfway = jobs.status(job);
    expect(halfway.state).toBe('PROCESSING');
    expect(halfway.progress).toBe(50);
    expect(halfway.stage).toBe('Validación por bacteriología');
    expect(halfway.etaSeconds).toBe(9);

    advance(9000);
    const done = jobs.status(job);
    expect(done).toMatchObject({ state: 'COMPLETED', progress: 100, etaSeconds: 0, recommendedIntervalMs: 0 });
    expect(done.result?.items).toHaveLength(4);
  });

  it('responde 304 lógico con el mismo ETag y cuenta las consultas desperdiciadas', () => {
    const { jobs, advance } = store();
    const job = jobs.create('RIPS', 'u-1');

    const first = jobs.poll(job.id, 'u-1', undefined);
    expect(first.modified).toBe(true);
    const second = jobs.poll(job.id, 'u-1', first.etag);
    expect(second.modified).toBe(false);
    expect(second.status).toMatchObject({ polls: 2, notModified: 1 });

    advance(5000);
    const third = jobs.poll(job.id, 'u-1', first.etag);
    expect(third.modified).toBe(true);
    expect(third.etag).not.toBe(first.etag);
  });

  it('aísla los trabajos por usuario y limita los trabajos activos', () => {
    const { jobs } = store();
    const job = jobs.create('LAB_RESULTS', 'u-1');
    expect(() => jobs.find(job.id, 'u-2')).toThrow(LabJobNotFound);
    expect(() => jobs.find('no-existe', 'u-1')).toThrow(LabJobNotFound);

    for (let i = 1; i < 20; i++) jobs.create('RIPS', 'u-1');
    expect(() => jobs.create('RIPS', 'u-1')).toThrow(LabJobLimitReached);
    expect(jobs.create('RIPS', 'u-2').ownerId).toBe('u-2');
  });

  it('elimina los trabajos con más de 30 minutos al crear uno nuevo', () => {
    const { jobs, advance } = store();
    jobs.create('LAB_RESULTS', 'u-1');
    advance(31 * 60_000);
    jobs.create('LAB_RESULTS', 'u-1');
    expect(jobs.size()).toBe(1);
  });
});
