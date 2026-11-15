import type { IncomingMessage } from 'node:http';
import { authenticate, type Principal } from '../auth/principal.js';
import type { GatewayConfig } from '../config.js';
import { HttpError, readJson, sendJson, type Router } from '../http/router.js';
import { LabJobLimitReached, LabJobNotFound, type LabJobKind, type LabJobStore } from './lab-jobs.js';

const KINDS: LabJobKind[] = ['LAB_RESULTS', 'RIPS'];

export function registerPollingRoutes(router: Router, config: GatewayConfig, jobs: LabJobStore): void {
  const principal = (request: IncomingMessage, url: URL): Principal =>
    authenticate(request, url, config.jwtSecret, config.jwtIssuer);

  router.post('/api/realtime/lab-jobs', async (request, response, url) => {
    const user = principal(request, url);
    const body = await readJson<{ kind?: string }>(request);
    const kind = (body.kind ?? 'LAB_RESULTS') as LabJobKind;
    if (!KINDS.includes(kind)) throw new HttpError(400, `Tipo de trabajo inválido: ${body.kind}`);
    try {
      const job = jobs.create(kind, user.userId);
      const status = jobs.status(job);
      sendJson(response, 202, status, {
        Location: `/api/realtime/lab-status-polling?jobId=${job.id}`,
        'Cache-Control': 'no-store',
        'X-Poll-Interval': String(config.pollingIntervalMs),
      });
    } catch (error) {
      if (error instanceof LabJobLimitReached) throw new HttpError(429, error.message);
      throw error;
    }
  });

  router.get('/api/realtime/lab-status-polling', (request, response, url) => {
    const user = principal(request, url);
    const jobId = url.searchParams.get('jobId');
    if (!jobId) throw new HttpError(400, 'Falta el parámetro jobId');
    try {
      const { status, etag, modified } = jobs.poll(jobId, user.userId, request.headers['if-none-match']);
      const headers = {
        ETag: etag,
        'Cache-Control': 'no-store',
        'X-Poll-Interval': String(status.recommendedIntervalMs),
        'X-Instance-Id': config.instanceId,
      };
      if (!modified) {
        response.writeHead(304, headers);
        response.end();
        return;
      }
      sendJson(response, 200, status, headers);
    } catch (error) {
      if (error instanceof LabJobNotFound) throw new HttpError(404, error.message);
      throw error;
    }
  });
}
