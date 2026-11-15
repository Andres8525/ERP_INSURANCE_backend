import { EventEmitter } from 'node:events';
import { afterEach, describe, expect, it } from 'vitest';
import { InMemoryBackplane } from '../src/backplane/memory.js';
import { createGateway, type Gateway } from '../src/server.js';
import { MemoryRecordStore } from '../src/ws/record-store.js';
import { testConfig, tokenFor, wait } from './support.js';
import { TestClient } from './ws-client.js';

let gateways: Gateway[] = [];
let clients: TestClient[] = [];

afterEach(async () => {
  await Promise.all(clients.map((client) => client.close()));
  for (const gateway of gateways) await gateway.close();
  clients = [];
  gateways = [];
});

async function cluster(count: number) {
  const redisSimulado = new EventEmitter();
  const historiasCompartidas = new MemoryRecordStore();
  const nodes: Array<{ gateway: Gateway; base: string }> = [];
  for (let index = 0; index < count; index++) {
    const gateway = await createGateway(testConfig({ instanceId: `gw-${String.fromCharCode(97 + index)}`, allowedOrigins: ['http://localhost:4200'] }), {
      backplane: new InMemoryBackplane(redisSimulado),
      records: historiasCompartidas,
    });
    gateways.push(gateway);
    const port = await gateway.listen(0, '127.0.0.1');
    nodes.push({ gateway, base: `http://127.0.0.1:${port}` });
  }
  return nodes;
}

async function connect(base: string, userId: string, role = 'ADMIN', query = ''): Promise<TestClient> {
  const client = await TestClient.connect(base, tokenFor(userId, role), query);
  clients.push(client);
  return client;
}

describe('medical hub: escalado horizontal sin sticky sessions', () => {
  it('una interconsulta del Médico 1 en el servidor A llega al Médico 2 en el servidor B y confirma la entrega', async () => {
    const [a, b] = await cluster(2);
    const medico1 = await connect(a!.base, 'medico-1');
    const medico2 = await connect(b!.base, 'medico-2');

    medico1.send({ type: 'consult.request', toUserId: 'medico-2', specialty: 'Cardiología', message: 'ECG con elevación del ST en cama 3', priority: 'URGENT' });

    const sent = await medico1.next('consult.sent');
    const request = await medico2.next('consult.request');
    expect(request).toMatchObject({
      consultId: sent.consultId,
      specialty: 'Cardiología',
      priority: 'URGENT',
      originInstance: 'gw-a',
      deliveredBy: 'gw-b',
      from: { userId: 'medico-1', clinicalRole: 'MEDICO' },
    });
    const delivered = await medico1.next('consult.delivered');
    expect(delivered).toMatchObject({ consultId: sent.consultId, deliveredByInstance: 'gw-b', devices: 1 });

    medico2.send({ type: 'consult.respond', consultId: sent.consultId, toUserId: 'medico-1', accepted: true, message: 'Voy en camino' });
    expect(await medico1.next('consult.response')).toMatchObject({ accepted: true, message: 'Voy en camino', deliveredBy: 'gw-a' });
  });

  it('chat de telemedicina entre réplicas: solo reciben los miembros de la sala', async () => {
    const [a, b] = await cluster(2);
    const doctor = await connect(a!.base, 'medico-1');
    const patient = await connect(b!.base, 'paciente-ana', 'AGENT');
    const outsider = await connect(b!.base, 'otro', 'AGENT');

    doctor.send({ type: 'chat.join', roomId: 'tele-ana' });
    await doctor.next('chat.joined');
    patient.send({ type: 'chat.join', roomId: 'tele-ana' });
    await patient.next('chat.joined');
    await doctor.next('chat.presence', (message) => (message.user as { userId: string }).userId === 'paciente-ana');

    doctor.send({ type: 'chat.message', roomId: 'tele-ana', text: '¿Cómo sigue la fiebre?' });
    expect(await patient.next('chat.message')).toMatchObject({ text: '¿Cómo sigue la fiebre?', from: { userId: 'medico-1' }, deliveredBy: 'gw-b' });
    expect(await doctor.next('chat.message')).toMatchObject({ deliveredBy: 'gw-a' });

    outsider.send({ type: 'chat.message', roomId: 'tele-ana', text: 'hola' });
    expect(await outsider.next('error')).toMatchObject({ code: 'NOT_IN_ROOM' });
    await wait(50);
    expect(outsider.received('chat.message')).toHaveLength(0);
  });

  it('bloqueo en vivo de la historia clínica compartido entre réplicas y liberado al desconectarse', async () => {
    const [a, b] = await cluster(2);
    const medico = await connect(a!.base, 'medico-1');
    const enfermera = await connect(b!.base, 'enfermera-1', 'AUDITOR');
    const recepcion = await connect(b!.base, 'recepcion-1', 'AGENT');

    enfermera.send({ type: 'record.open', recordId: 'HC-1001' });
    expect(await enfermera.next('record.state')).toMatchObject({ record: { version: 0 }, lock: null });

    medico.send({ type: 'record.lock', recordId: 'HC-1001' });
    expect(await enfermera.next('record.locked')).toMatchObject({ lock: { userId: 'medico-1' } });

    enfermera.send({ type: 'record.lock', recordId: 'HC-1001' });
    expect(await enfermera.next('record.lock-denied')).toMatchObject({ lock: { userId: 'medico-1' } });
    enfermera.send({ type: 'record.edit', recordId: 'HC-1001', field: 'plan', value: 'Alta', baseVersion: 0 });
    expect(await enfermera.next('record.conflict')).toMatchObject({ reason: 'NOT_LOCKED' });

    medico.send({ type: 'record.edit', recordId: 'HC-1001', field: 'diagnostico', value: 'Infarto agudo de miocardio', baseVersion: 0 });
    expect(await enfermera.next('record.updated')).toMatchObject({ field: 'diagnostico', record: { version: 1, fields: { diagnostico: 'Infarto agudo de miocardio' } } });
    medico.send({ type: 'record.edit', recordId: 'HC-1001', field: 'plan', value: 'Cateterismo', baseVersion: 0 });
    expect(await medico.next('record.conflict')).toMatchObject({ reason: 'STALE', record: { version: 1 } });

    recepcion.send({ type: 'record.lock', recordId: 'HC-1001' });
    expect(await recepcion.next('error')).toMatchObject({ code: 'FORBIDDEN' });

    await medico.close();
    expect(await enfermera.next('record.unlocked')).toMatchObject({ reason: 'Conexión cerrada' });
    enfermera.send({ type: 'record.lock', recordId: 'HC-1001' });
    expect(await enfermera.next('record.locked')).toMatchObject({ lock: { userId: 'enfermera-1' } });
  });

  it('Código Azul por sede: fan-out en cada réplica con reporte agregado al emisor', async () => {
    const [a, b] = await cluster(2);
    const medico = await connect(a!.base, 'medico-1', 'ADMIN', 'sede=SEDE-MIA');
    const enfermeraMia = await connect(b!.base, 'enfermera-1', 'AUDITOR', 'sede=SEDE-MIA');
    const enfermeraOrl = await connect(b!.base, 'enfermera-2', 'AUDITOR', 'sede=SEDE-ORL');

    const virtual = await fetch(`${b!.base}/api/realtime/load-test/terminals?count=3000`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${tokenFor('admin')}` },
    });
    expect(virtual.status).toBe(201);

    medico.send({ type: 'alert.code-blue', location: 'UCI cama 3', scope: 'sede' });
    expect(await enfermeraMia.next('alert.code-blue')).toMatchObject({ location: 'UCI cama 3', sedeId: 'SEDE-MIA', originInstance: 'gw-a' });
    const reports = [await medico.next('fanout.report'), await medico.next('fanout.report')];
    const fromB = reports.find((report) => report.instanceId === 'gw-b')!;
    expect((fromB.report as { delivered: number; batches: number }).delivered).toBe(1001);
    expect((fromB.report as { batches: number }).batches).toBeGreaterThan(1);
    await wait(50);
    expect(enfermeraOrl.received('alert.code-blue')).toHaveLength(0);

    const metrics = (await (await fetch(`${b!.base}/api/realtime/metrics`, { headers: { Authorization: `Bearer ${tokenFor('admin')}` } })).json()) as {
      websocket: { virtualTerminals: number; lastFanOut: { event: string; delivered: number } };
      backplane: { kind: string };
    };
    expect(metrics.websocket.virtualTerminals).toBe(3000);
    expect(metrics.websocket.lastFanOut).toMatchObject({ event: 'alert.code-blue', delivered: 1001 });
    expect(metrics.backplane.kind).toBe('memory');
  });
});

describe('medical hub: conexión y ciclo de vida', () => {
  it('rechaza el upgrade sin token, con origen no permitido o en otra ruta', async () => {
    const [a] = await cluster(1);
    await expect(TestClient.connect(a!.base, '', '', false)).rejects.toThrow('HTTP 401');
    await expect(TestClient.connect(a!.base, tokenFor('u'), 'clinicalRole=CIRUJANO')).rejects.toThrow('HTTP 403');

    const WebSocket = (await import('ws')).default;
    const evil = new WebSocket(`${a!.base.replace('http', 'ws')}/ws/medical-hub`, ['bearer', tokenFor('u')], { origin: 'https://evil.example' });
    const status = await new Promise<number>((resolve) => evil.once('unexpected-response', (_req, res) => resolve(res.statusCode ?? 0)));
    expect(status).toBe(403);
  });

  it('registra la conexión con rol y sede, responde ping y la limpia al cerrar', async () => {
    const [a] = await cluster(1);
    const client = await connect(a!.base, 'recepcion-1', 'AGENT', 'sede=SEDE-TPA');
    expect(a!.gateway.hub.registry.snapshot()).toMatchObject({ connections: 1, bySede: { 'SEDE-TPA': 1 }, byRole: { RECEPCION: 1 } });

    client.send({ type: 'ping', sentAt: 123 });
    expect(await client.next('pong')).toMatchObject({ sentAt: 123, instanceId: 'gw-a' });

    client.send({ type: 'turns.call', ticket: 'A-042', room: 'Consultorio 3' });
    expect(await client.next('turns.called')).toMatchObject({ ticket: 'A-042', sedeId: 'SEDE-TPA' });
    client.send({ type: 'alert.code-blue', location: 'Sala de espera' });
    expect(await client.next('error')).toMatchObject({ code: 'FORBIDDEN' });

    await client.close();
    await wait(30);
    expect(a!.gateway.hub.registry.size).toBe(0);
  });

  it('limita la tasa de mensajes por conexión y valida el contenido', async () => {
    const [a] = await cluster(1);
    const client = await connect(a!.base, 'medico-1');
    client.send({ type: 'desconocido' });
    expect(await client.next('error')).toMatchObject({ code: 'BAD_REQUEST', message: 'Tipo de mensaje desconocido: desconocido' });

    for (let index = 0; index < 60; index++) client.send({ type: 'ping', sentAt: index });
    expect(await client.next('error', (message) => message.code === 'RATE_LIMITED')).toBeTruthy();
    expect(a!.gateway.hub.stats().rateLimited).toBeGreaterThan(0);
  });

  it('el heartbeat termina conexiones que no responden pong', async () => {
    const [a] = await cluster(1);
    const client = await connect(a!.base, 'medico-1');
    client.socket.pong = () => undefined;
    a!.gateway.hub.sweepHeartbeat();
    await wait(50);
    a!.gateway.hub.sweepHeartbeat();
    await wait(50);
    expect(a!.gateway.hub.registry.size).toBe(0);
    expect(a!.gateway.hub.stats().heartbeatTerminated).toBe(1);
  });
});
