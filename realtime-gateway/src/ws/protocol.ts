export const RECORD_FIELDS = ['motivoConsulta', 'diagnostico', 'evolucion', 'plan'] as const;
export type RecordField = (typeof RECORD_FIELDS)[number];

export type ClientMessage =
  | { type: 'ping'; sentAt: number }
  | { type: 'chat.join'; roomId: string }
  | { type: 'chat.leave'; roomId: string }
  | { type: 'chat.message'; roomId: string; text: string }
  | { type: 'chat.typing'; roomId: string }
  | { type: 'record.open'; recordId: string }
  | { type: 'record.lock'; recordId: string }
  | { type: 'record.edit'; recordId: string; field: RecordField; value: string; baseVersion: number }
  | { type: 'record.unlock'; recordId: string }
  | { type: 'consult.request'; toUserId: string; specialty: string; message: string; priority: 'ROUTINE' | 'URGENT' }
  | { type: 'consult.respond'; consultId: string; toUserId: string; accepted: boolean; message: string }
  | { type: 'alert.code-blue'; location: string; scope: 'sede' | 'all' }
  | { type: 'turns.call'; ticket: string; room: string };

export type ClientMessageType = ClientMessage['type'];

export type ParseResult = { ok: true; message: ClientMessage } | { ok: false; error: string };

const ROOM_ID = /^tele-[a-z0-9-]{1,40}$/;
const RECORD_ID = /^HC-[A-Z0-9-]{1,40}$/;
const USER_ID = /^[A-Za-z0-9-]{1,64}$/;

class InvalidMessage extends Error {}

function text(value: unknown, field: string, min: number, max: number): string {
  if (typeof value !== 'string') throw new InvalidMessage(`${field} debe ser texto`);
  const trimmed = value.trim();
  if (trimmed.length < min || trimmed.length > max) throw new InvalidMessage(`${field} debe tener entre ${min} y ${max} caracteres`);
  return trimmed;
}

function pattern(value: unknown, field: string, regex: RegExp): string {
  if (typeof value !== 'string' || !regex.test(value)) throw new InvalidMessage(`${field} con formato inválido`);
  return value;
}

function oneOf<T extends string>(value: unknown, field: string, allowed: readonly T[]): T {
  if (!allowed.includes(value as T)) throw new InvalidMessage(`${field} debe ser ${allowed.join(', ')}`);
  return value as T;
}

function validate(data: Record<string, unknown>): ClientMessage {
  switch (data.type) {
    case 'ping':
      return { type: 'ping', sentAt: typeof data.sentAt === 'number' ? data.sentAt : Date.now() };
    case 'chat.join':
    case 'chat.leave':
    case 'chat.typing':
      return { type: data.type, roomId: pattern(data.roomId, 'roomId', ROOM_ID) };
    case 'chat.message':
      return { type: 'chat.message', roomId: pattern(data.roomId, 'roomId', ROOM_ID), text: text(data.text, 'text', 1, 2000) };
    case 'record.open':
    case 'record.lock':
    case 'record.unlock':
      return { type: data.type, recordId: pattern(data.recordId, 'recordId', RECORD_ID) };
    case 'record.edit': {
      if (!Number.isInteger(data.baseVersion) || (data.baseVersion as number) < 0) throw new InvalidMessage('baseVersion debe ser un entero >= 0');
      return {
        type: 'record.edit',
        recordId: pattern(data.recordId, 'recordId', RECORD_ID),
        field: oneOf(data.field, 'field', RECORD_FIELDS),
        value: text(data.value, 'value', 0, 4000),
        baseVersion: data.baseVersion as number,
      };
    }
    case 'consult.request':
      return {
        type: 'consult.request',
        toUserId: pattern(data.toUserId, 'toUserId', USER_ID),
        specialty: text(data.specialty, 'specialty', 2, 60),
        message: text(data.message, 'message', 1, 1000),
        priority: oneOf(data.priority ?? 'ROUTINE', 'priority', ['ROUTINE', 'URGENT'] as const),
      };
    case 'consult.respond':
      if (typeof data.accepted !== 'boolean') throw new InvalidMessage('accepted debe ser booleano');
      return {
        type: 'consult.respond',
        consultId: text(data.consultId, 'consultId', 1, 64),
        toUserId: pattern(data.toUserId, 'toUserId', USER_ID),
        accepted: data.accepted,
        message: text(data.message ?? '', 'message', 0, 1000),
      };
    case 'alert.code-blue':
      return {
        type: 'alert.code-blue',
        location: text(data.location, 'location', 2, 80),
        scope: oneOf(data.scope ?? 'sede', 'scope', ['sede', 'all'] as const),
      };
    case 'turns.call':
      return { type: 'turns.call', ticket: text(data.ticket, 'ticket', 1, 12), room: text(data.room, 'room', 1, 40) };
    default:
      throw new InvalidMessage(`Tipo de mensaje desconocido: ${String(data.type)}`);
  }
}

export function parseClientMessage(raw: string, maxBytes: number): ParseResult {
  if (Buffer.byteLength(raw) > maxBytes) return { ok: false, error: 'Mensaje demasiado grande' };
  let data: unknown;
  try {
    data = JSON.parse(raw);
  } catch {
    return { ok: false, error: 'JSON inválido' };
  }
  if (!data || typeof data !== 'object' || Array.isArray(data)) return { ok: false, error: 'El mensaje debe ser un objeto' };
  try {
    return { ok: true, message: validate(data as Record<string, unknown>) };
  } catch (error) {
    if (error instanceof InvalidMessage) return { ok: false, error: error.message };
    throw error;
  }
}

export interface ServerMessage {
  type: string;
  [key: string]: unknown;
}

export function serialize(message: ServerMessage): string {
  return JSON.stringify(message);
}
