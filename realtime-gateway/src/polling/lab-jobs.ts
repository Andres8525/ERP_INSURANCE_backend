import { randomUUID } from 'node:crypto';

export type LabJobKind = 'LAB_RESULTS' | 'RIPS';
export type LabJobState = 'QUEUED' | 'PROCESSING' | 'COMPLETED';

export interface LabJob {
  id: string;
  kind: LabJobKind;
  ownerId: string;
  createdAt: number;
  durationMs: number;
  polls: number;
  notModified: number;
}

export interface LabJobStatus {
  jobId: string;
  kind: LabJobKind;
  state: LabJobState;
  progress: number;
  stage: string;
  etaSeconds: number;
  polls: number;
  notModified: number;
  recommendedIntervalMs: number;
  result?: { summary: string; items: string[] };
}

const DURATION_MS: Record<LabJobKind, number> = { LAB_RESULTS: 18_000, RIPS: 30_000 };
const QUEUE_MS = 1_500;
const RETENTION_MS = 30 * 60_000;
const MAX_JOBS_PER_OWNER = 20;

const STAGES: Record<LabJobKind, string[]> = {
  LAB_RESULTS: ['Recepción de muestras', 'Análisis en equipos', 'Validación por bacteriología', 'Firma del resultado'],
  RIPS: ['Consolidación de atenciones', 'Validación de códigos CIE-10', 'Cruce con tarifas', 'Generación de archivos RIPS'],
};

const RESULTS: Record<LabJobKind, { summary: string; items: string[] }> = {
  LAB_RESULTS: {
    summary: 'Panel metabólico y hemograma listos: 2 valores fuera de rango',
    items: ['Glucosa en ayunas 128 mg/dL (alta)', 'Hemoglobina 13.9 g/dL', 'Creatinina 1.4 mg/dL (alta)', 'Leucocitos 7.200/µL'],
  },
  RIPS: {
    summary: 'RIPS generado: 4 archivos y 1.284 registros validados',
    items: ['AF - transacciones', 'US - usuarios', 'AC - consultas', 'AP - procedimientos'],
  },
};

export class LabJobNotFound extends Error {}
export class LabJobLimitReached extends Error {}

export class LabJobStore {
  private readonly jobs = new Map<string, LabJob>();

  constructor(
    private readonly pollingIntervalMs: number,
    private readonly clock: () => number = Date.now,
  ) {}

  create(kind: LabJobKind, ownerId: string): LabJob {
    this.prune();
    const owned = [...this.jobs.values()].filter((job) => job.ownerId === ownerId);
    if (owned.length >= MAX_JOBS_PER_OWNER) throw new LabJobLimitReached('Demasiados trabajos activos');
    const job: LabJob = {
      id: randomUUID(),
      kind,
      ownerId,
      createdAt: this.clock(),
      durationMs: DURATION_MS[kind],
      polls: 0,
      notModified: 0,
    };
    this.jobs.set(job.id, job);
    return job;
  }

  find(jobId: string, ownerId: string): LabJob {
    const job = this.jobs.get(jobId);
    if (!job || job.ownerId !== ownerId) throw new LabJobNotFound('Trabajo no encontrado');
    return job;
  }

  status(job: LabJob): LabJobStatus {
    const elapsed = this.clock() - job.createdAt;
    const processing = Math.max(0, elapsed - QUEUE_MS);
    const progress = elapsed < QUEUE_MS ? 0 : Math.min(100, Math.floor((processing / job.durationMs) * 100));
    const state: LabJobState = elapsed < QUEUE_MS ? 'QUEUED' : progress >= 100 ? 'COMPLETED' : 'PROCESSING';
    const stages = STAGES[job.kind];
    const stage = state === 'QUEUED' ? 'En cola' : state === 'COMPLETED' ? 'Completado'
      : stages[Math.min(stages.length - 1, Math.floor((progress / 100) * stages.length))] ?? 'Procesando';
    return {
      jobId: job.id,
      kind: job.kind,
      state,
      progress,
      stage,
      etaSeconds: state === 'COMPLETED' ? 0 : Math.ceil((job.durationMs + QUEUE_MS - elapsed) / 1000),
      polls: job.polls,
      notModified: job.notModified,
      recommendedIntervalMs: state === 'COMPLETED' ? 0 : this.pollingIntervalMs,
      ...(state === 'COMPLETED' ? { result: RESULTS[job.kind] } : {}),
    };
  }

  etag(status: LabJobStatus): string {
    return `W/"${status.jobId}-${status.state}-${status.progress}"`;
  }

  poll(jobId: string, ownerId: string, ifNoneMatch: string | undefined): { status: LabJobStatus; etag: string; modified: boolean } {
    const job = this.find(jobId, ownerId);
    job.polls += 1;
    const current = this.status(job);
    const etag = this.etag(current);
    const modified = ifNoneMatch !== etag;
    if (!modified) job.notModified += 1;
    return { status: { ...current, polls: job.polls, notModified: job.notModified }, etag, modified };
  }

  size(): number {
    return this.jobs.size;
  }

  private prune(): void {
    const limit = this.clock() - RETENTION_MS;
    for (const [id, job] of this.jobs) {
      if (job.createdAt < limit) this.jobs.delete(id);
    }
  }
}
