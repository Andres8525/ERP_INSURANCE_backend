import type { IncomingMessage } from 'node:http';
import type { Duplex } from 'node:stream';
import { randomUUID } from 'node:crypto';
import { WebSocketServer, type RawData, type WebSocket } from 'ws';
import { AuthError, authenticate, type Principal } from '../auth/principal.js';
import { envelope, type Backplane, type BackplaneEnvelope, type DeliveryTarget } from '../backplane/backplane.js';
import type { GatewayConfig } from '../config.js';
import { ConnectionRegistry, OPEN, type AuthenticatedSocket } from './connection-registry.js';
import { fanOut, type FanOutReport } from './fan-out.js';
import { Heartbeat } from './heartbeat.js';
import { parseClientMessage, serialize, type ClientMessage, type ServerMessage } from './protocol.js';
import type { RecordStore } from './record-store.js';

export const HUB_PATH = '/ws/medical-hub';
const FAN_OUT_EVENTS = new Set(['alert.code-blue', 'turns.called']);
const RATE_PER_SECOND = 20;
const RATE_BURST = 40;

interface Sender {
  userId: string;
  email: string;
  clinicalRole: string;
  sedeId: string;
}

interface RateBucket {
  tokens: number;
  updatedAt: number;
}

export interface HubStats {
  messagesIn: number;
  messagesRejected: number;
  rateLimited: number;
  envelopesReceived: number;
  lastFanOut: (FanOutReport & { event: string; at: string }) | null;
  heartbeatTerminated: number;
}

const recordRoom = (recordId: string) => `hc:${recordId}`;

function sender(principal: Principal): Sender {
  return { userId: principal.userId, email: principal.email, clinicalRole: principal.clinicalRole, sedeId: principal.sedeId };
}

export class MedicalHub {
  readonly registry = new ConnectionRegistry();
  private readonly server: WebSocketServer;
  private readonly heartbeat: Heartbeat;
  private readonly buckets = new Map<string, RateBucket>();
  private unsubscribe: (() => void) | null = null;
  private readonly counters: HubStats = {
    messagesIn: 0,
    messagesRejected: 0,
    rateLimited: 0,
    envelopesReceived: 0,
    lastFanOut: null,
    heartbeatTerminated: 0,
  };

  constructor(
    private readonly config: GatewayConfig,
    private readonly backplane: Backplane,
    private readonly records: RecordStore,
  ) {
    this.server = new WebSocketServer({
      noServer: true,
      maxPayload: config.maxPayloadBytes,
      perMessageDeflate: false,
      handleProtocols: (protocols) => (protocols.has('bearer') ? 'bearer' : false),
    });
    this.heartbeat = new Heartbeat(this.registry, config.heartbeatIntervalMs, (entry) => void this.cleanup(entry));
  }

  async start(): Promise<void> {
    this.unsubscribe = await this.backplane.subscribe((message) => void this.deliver(message));
    this.heartbeat.start();
  }

  handleUpgrade(request: IncomingMessage, socket: Duplex, head: Buffer): void {
    const url = new URL(request.url ?? '/', 'http://gateway.local');
    const reject = (status: number, reason: string) => {
      socket.write(`HTTP/1.1 ${status} ${reason}\r\nConnection: close\r\nContent-Length: 0\r\n\r\n`);
      socket.destroy();
    };
    if (url.pathname !== HUB_PATH) return reject(404, 'Not Found');
    const origin = request.headers.origin;
    if (origin && !this.config.allowedOrigins.includes(origin) && !this.config.allowedOrigins.includes('*')) {
      return reject(403, 'Forbidden');
    }
    let principal: Principal;
    try {
      principal = authenticate(request, url, this.config.jwtSecret, this.config.jwtIssuer);
    } catch (error) {
      const status = error instanceof AuthError ? error.status : 401;
      return reject(status, status === 403 ? 'Forbidden' : 'Unauthorized');
    }
    this.server.handleUpgrade(request, socket, head, (ws) => this.onConnection(ws, principal, request));
  }

  stats(): HubStats {
    return { ...this.counters, heartbeatTerminated: this.heartbeat.terminated };
  }

  sweepHeartbeat() {
    return this.heartbeat.sweep();
  }

  async close(): Promise<void> {
    this.heartbeat.stop();
    this.unsubscribe?.();
    for (const entry of [...this.registry.values()]) {
      entry.socket.close(1001, 'Gateway reiniciándose');
      await this.cleanup(entry);
    }
    await new Promise<void>((resolve) => this.server.close(() => resolve()));
  }

  publish(target: DeliveryTarget, event: string, payload: Record<string, unknown>): Promise<void> {
    return this.backplane.publish(envelope(this.config.instanceId, target, event, payload));
  }

  private onConnection(ws: WebSocket, principal: Principal, request: IncomingMessage): void {
    const entry = this.registry.add(ws, principal, request.socket.remoteAddress ?? null);
    const release = () => void this.cleanup(entry);
    ws.on('pong', () => this.registry.touch(entry.connectionId));
    ws.on('message', (data: RawData, isBinary: boolean) => void this.onMessage(entry, data, isBinary));
    ws.on('close', release);
    ws.on('error', release);
    this.send(entry, {
      type: 'welcome',
      connectionId: entry.connectionId,
      instanceId: this.config.instanceId,
      principal: sender(principal),
      backplane: { kind: this.backplane.kind, fallbackReason: this.backplane.fallbackReason },
      heartbeatMs: this.config.heartbeatIntervalMs,
    });
  }

  private async cleanup(entry: AuthenticatedSocket): Promise<void> {
    const removed = this.registry.remove(entry.connectionId) ?? (this.registry.get(entry.connectionId) ? null : entry);
    this.buckets.delete(entry.connectionId);
    if (!removed) return;
    const released = await this.records.releaseByConnection(entry.connectionId).catch(() => [] as string[]);
    for (const recordId of released) {
      await this.publish({ kind: 'room', roomId: recordRoom(recordId) }, 'record.unlocked', {
        recordId,
        by: sender(entry.principal),
        reason: 'Conexión cerrada',
      }).catch(() => undefined);
    }
  }

  private allow(entry: AuthenticatedSocket): boolean {
    const now = Date.now();
    const bucket = this.buckets.get(entry.connectionId) ?? { tokens: RATE_BURST, updatedAt: now };
    bucket.tokens = Math.min(RATE_BURST, bucket.tokens + ((now - bucket.updatedAt) / 1000) * RATE_PER_SECOND);
    bucket.updatedAt = now;
    this.buckets.set(entry.connectionId, bucket);
    if (bucket.tokens < 1) return false;
    bucket.tokens -= 1;
    return true;
  }

  private async onMessage(entry: AuthenticatedSocket, data: RawData, isBinary: boolean): Promise<void> {
    this.counters.messagesIn += 1;
    entry.messagesIn += 1;
    this.registry.touch(entry.connectionId);
    if (isBinary) return this.reject(entry, 'Solo se aceptan mensajes de texto JSON');
    if (!this.allow(entry)) {
      this.counters.rateLimited += 1;
      return this.reject(entry, 'Demasiados mensajes: espera un momento', 'RATE_LIMITED');
    }
    const parsed = parseClientMessage(data.toString(), this.config.maxPayloadBytes);
    if (!parsed.ok) return this.reject(entry, parsed.error);
    try {
      await this.dispatch(entry, parsed.message);
    } catch (error) {
      console.error('[medical-hub] error procesando mensaje', error);
      this.reject(entry, 'Error interno procesando el mensaje', 'INTERNAL');
    }
  }

  private reject(entry: AuthenticatedSocket, message: string, code = 'BAD_REQUEST'): void {
    this.counters.messagesRejected += 1;
    this.send(entry, { type: 'error', code, message });
  }

  private requireRole(entry: AuthenticatedSocket, roles: string[], action: string): boolean {
    if (roles.includes(entry.principal.clinicalRole)) return true;
    this.reject(entry, `El rol ${entry.principal.clinicalRole} no puede ${action}`, 'FORBIDDEN');
    return false;
  }

  private async dispatch(entry: AuthenticatedSocket, message: ClientMessage): Promise<void> {
    const me = sender(entry.principal);
    const owner = { userId: me.userId, email: me.email, connectionId: entry.connectionId };
    switch (message.type) {
      case 'ping':
        this.send(entry, { type: 'pong', sentAt: message.sentAt, serverAt: Date.now(), instanceId: this.config.instanceId });
        return;

      case 'chat.join':
        this.registry.join(entry.connectionId, message.roomId);
        this.send(entry, { type: 'chat.joined', roomId: message.roomId });
        await this.publish({ kind: 'room', roomId: message.roomId }, 'chat.presence', { roomId: message.roomId, user: me, status: 'joined' });
        return;

      case 'chat.leave':
        this.registry.leave(entry.connectionId, message.roomId);
        await this.publish({ kind: 'room', roomId: message.roomId }, 'chat.presence', { roomId: message.roomId, user: me, status: 'left' });
        return;

      case 'chat.message':
      case 'chat.typing':
        if (!entry.rooms.has(message.roomId)) return this.reject(entry, 'Primero únete a la sala de telemedicina', 'NOT_IN_ROOM');
        await this.publish({ kind: 'room', roomId: message.roomId }, message.type, {
          roomId: message.roomId,
          from: me,
          ...(message.type === 'chat.message' ? { messageId: randomUUID(), text: message.text, sentAt: new Date().toISOString() } : {}),
        });
        return;

      case 'record.open': {
        this.registry.join(entry.connectionId, recordRoom(message.recordId));
        const [record, lock] = await Promise.all([this.records.get(message.recordId), this.records.currentLock(message.recordId)]);
        this.send(entry, { type: 'record.state', record, lock });
        return;
      }

      case 'record.lock': {
        if (!this.requireRole(entry, ['MEDICO', 'ENFERMERO'], 'editar historias clínicas')) return;
        this.registry.join(entry.connectionId, recordRoom(message.recordId));
        const result = await this.records.acquireLock(message.recordId, owner, this.config.lockTtlMs);
        if (!result.acquired) {
          this.send(entry, { type: 'record.lock-denied', recordId: message.recordId, lock: result.lock });
          return;
        }
        await this.publish({ kind: 'room', roomId: recordRoom(message.recordId) }, 'record.locked', { recordId: message.recordId, lock: result.lock });
        return;
      }

      case 'record.edit': {
        const result = await this.records.applyEdit(message.recordId, message.field, message.value, message.baseVersion, owner);
        if (!result.ok) {
          this.send(entry, { type: 'record.conflict', recordId: message.recordId, reason: result.reason, record: result.record, lock: result.lock });
          return;
        }
        await this.records.acquireLock(message.recordId, owner, this.config.lockTtlMs);
        await this.publish({ kind: 'room', roomId: recordRoom(message.recordId) }, 'record.updated', {
          recordId: message.recordId,
          field: message.field,
          record: result.record,
          by: me,
        });
        return;
      }

      case 'record.unlock':
        if (await this.records.releaseLock(message.recordId, entry.connectionId)) {
          await this.publish({ kind: 'room', roomId: recordRoom(message.recordId) }, 'record.unlocked', { recordId: message.recordId, by: me, reason: 'Liberado' });
        }
        return;

      case 'consult.request': {
        if (!this.requireRole(entry, ['MEDICO'], 'solicitar interconsultas')) return;
        if (message.toUserId === me.userId) return this.reject(entry, 'No puedes enviarte una interconsulta a ti mismo');
        const consultId = randomUUID();
        this.send(entry, { type: 'consult.sent', consultId, toUserId: message.toUserId, viaInstance: this.config.instanceId });
        await this.publish({ kind: 'user', userId: message.toUserId }, 'consult.request', {
          consultId,
          from: me,
          specialty: message.specialty,
          message: message.message,
          priority: message.priority,
          requestedAt: new Date().toISOString(),
        });
        return;
      }

      case 'consult.respond':
        await this.publish({ kind: 'user', userId: message.toUserId }, 'consult.response', {
          consultId: message.consultId,
          from: me,
          accepted: message.accepted,
          message: message.message,
          respondedAt: new Date().toISOString(),
        });
        return;

      case 'alert.code-blue': {
        if (!this.requireRole(entry, ['MEDICO', 'ENFERMERO'], 'activar un Código Azul')) return;
        const target: DeliveryTarget = message.scope === 'all' ? { kind: 'all' } : { kind: 'sede', sedeId: me.sedeId };
        await this.publish(target, 'alert.code-blue', {
          alertId: randomUUID(),
          location: message.location,
          sedeId: me.sedeId,
          scope: message.scope,
          raisedBy: me,
          raisedAt: new Date().toISOString(),
        });
        return;
      }

      case 'turns.call':
        if (entry.principal.clinicalRole !== 'RECEPCION' && entry.principal.systemRole !== 'ADMIN') {
          return this.reject(entry, 'Solo recepción puede llamar turnos', 'FORBIDDEN');
        }
        await this.publish({ kind: 'sede', sedeId: me.sedeId }, 'turns.called', {
          callId: randomUUID(),
          ticket: message.ticket,
          room: message.room,
          sedeId: me.sedeId,
          raisedBy: me,
          calledAt: new Date().toISOString(),
        });
        return;
    }
  }

  private async deliver(message: BackplaneEnvelope): Promise<void> {
    this.counters.envelopesReceived += 1;
    const targets = this.registry.resolve(message.target);
    const payload = message.payload as Record<string, unknown>;
    const body = serialize({ ...payload, type: message.event, envelopeId: message.id, originInstance: message.origin, deliveredBy: this.config.instanceId });

    const report = await fanOut(targets, body, {
      batchSize: FAN_OUT_EVENTS.has(message.event) ? this.config.fanOutBatchSize : Number.MAX_SAFE_INTEGER,
      maxBufferedBytes: this.config.maxBufferedBytes,
    });
    for (const target of targets) target.messagesOut += 1;

    if (FAN_OUT_EVENTS.has(message.event)) {
      this.counters.lastFanOut = { ...report, event: message.event, at: new Date().toISOString() };
      const raisedBy = payload.raisedBy as Sender | undefined;
      if (raisedBy) {
        await this.publish({ kind: 'user', userId: raisedBy.userId }, 'fanout.report', {
          event: message.event,
          referenceId: payload.alertId ?? payload.callId,
          instanceId: this.config.instanceId,
          report,
        }).catch(() => undefined);
      }
    }

    if (message.event === 'consult.request' && report.delivered > 0) {
      const from = payload.from as Sender;
      await this.publish({ kind: 'user', userId: from.userId }, 'consult.delivered', {
        consultId: payload.consultId,
        toUserId: (message.target as { userId: string }).userId,
        devices: report.delivered,
        deliveredByInstance: this.config.instanceId,
      }).catch(() => undefined);
    }
  }

  private send(entry: AuthenticatedSocket, message: ServerMessage): void {
    if (entry.socket.readyState !== OPEN) return;
    entry.socket.send(serialize(message));
    entry.messagesOut += 1;
  }
}
