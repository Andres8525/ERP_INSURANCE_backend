import { authenticate } from '../auth/principal.js';
import type { GatewayConfig } from '../config.js';
import { HttpError, type Router } from '../http/router.js';
import type { SseHub } from './sse-hub.js';
import { ICU_BEDS } from './vitals.js';

const KNOWN_BEDS = new Set(ICU_BEDS.map((bed) => bed.bedId));

export function parseLastEventId(header: string | string[] | undefined, query: string | null): number | null {
  const raw = (Array.isArray(header) ? header[0] : header) ?? query;
  if (raw === undefined || raw === null || raw === '') return null;
  const value = Number.parseInt(raw, 10);
  return Number.isFinite(value) && value >= 0 ? value : null;
}

export function registerSseRoutes(router: Router, config: GatewayConfig, hub: SseHub): void {
  router.get('/api/realtime/vitals-stream', (request, response, url) => {
    const principal = authenticate(request, url, config.jwtSecret, config.jwtIssuer);
    if (principal.clinicalRole === 'RECEPCION') {
      throw new HttpError(403, 'Recepción no tiene acceso a signos vitales');
    }
    const beds = url.searchParams.get('beds');
    let bedIds: Set<string> | null = null;
    if (beds) {
      bedIds = new Set(beds.split(',').map((bed) => bed.trim()).filter(Boolean));
      const unknown = [...bedIds].filter((bed) => !KNOWN_BEDS.has(bed));
      if (unknown.length) throw new HttpError(400, `Camas desconocidas: ${unknown.join(', ')}`);
    }
    const sede = url.searchParams.get('scope') === 'sede' ? principal.sedeId : null;
    response.setHeader('X-Instance-Id', config.instanceId);
    hub.connect(response, principal, { bedIds, sedeId: sede }, parseLastEventId(request.headers['last-event-id'], url.searchParams.get('lastEventId')));
  });

  router.get('/api/realtime/icu-beds', (request, response, url) => {
    authenticate(request, url, config.jwtSecret, config.jwtIssuer);
    const body = JSON.stringify(ICU_BEDS.map(({ bedId, sedeId, patientLabel }) => ({ bedId, sedeId, patientLabel })));
    response.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'private, max-age=300' });
    response.end(body);
  });
}
