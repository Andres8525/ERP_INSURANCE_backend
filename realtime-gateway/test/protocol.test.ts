import { describe, expect, it } from 'vitest';
import { parseClientMessage } from '../src/ws/protocol.js';

const parse = (value: unknown) => parseClientMessage(JSON.stringify(value), 64 * 1024);

describe('protocolo del medical hub', () => {
  it('acepta y normaliza mensajes válidos de chat, historia clínica, interconsulta y alertas', () => {
    expect(parse({ type: 'chat.message', roomId: 'tele-ana-1', text: '  Buenos días  ' })).toEqual({
      ok: true,
      message: { type: 'chat.message', roomId: 'tele-ana-1', text: 'Buenos días' },
    });
    expect(parse({ type: 'record.edit', recordId: 'HC-1001', field: 'plan', value: 'Control en 48 h', baseVersion: 3 })).toMatchObject({ ok: true });
    expect(parse({ type: 'consult.request', toUserId: 'medico-2', specialty: 'Cardiología', message: 'Dolor torácico' })).toMatchObject({
      ok: true,
      message: { priority: 'ROUTINE' },
    });
    expect(parse({ type: 'alert.code-blue', location: 'UCI cama 3' })).toMatchObject({ ok: true, message: { scope: 'sede' } });
    expect(parse({ type: 'consult.respond', consultId: 'c-1', toUserId: 'medico-1', accepted: true })).toMatchObject({ ok: true, message: { message: '' } });
    expect(parse({ type: 'turns.call', ticket: 'A-042', room: 'Consultorio 3' })).toMatchObject({ ok: true });
    expect(parse({ type: 'ping' })).toMatchObject({ ok: true, message: { type: 'ping' } });
  });

  it('rechaza formatos, campos y tipos inválidos con mensajes claros', () => {
    expect(parse({ type: 'chat.join', roomId: 'sala general' })).toEqual({ ok: false, error: 'roomId con formato inválido' });
    expect(parse({ type: 'chat.message', roomId: 'tele-1', text: '' })).toEqual({ ok: false, error: 'text debe tener entre 1 y 2000 caracteres' });
    expect(parse({ type: 'record.edit', recordId: 'HC-1', field: 'contraseña', value: 'x', baseVersion: 0 })).toMatchObject({ ok: false });
    expect(parse({ type: 'record.edit', recordId: 'HC-1', field: 'plan', value: 'x', baseVersion: -1 })).toEqual({ ok: false, error: 'baseVersion debe ser un entero >= 0' });
    expect(parse({ type: 'record.open', recordId: '../../etc' })).toMatchObject({ ok: false });
    expect(parse({ type: 'consult.request', toUserId: 'x y', specialty: 'Cardio', message: 'a' })).toMatchObject({ ok: false });
    expect(parse({ type: 'alert.code-blue', location: 'UCI', scope: 'planeta' })).toMatchObject({ ok: false });
    expect(parse({ type: 'consult.respond', consultId: 'c', toUserId: 'u', accepted: 'si' })).toEqual({ ok: false, error: 'accepted debe ser booleano' });
    expect(parse({ type: 'drop.database' })).toEqual({ ok: false, error: 'Tipo de mensaje desconocido: drop.database' });
    expect(parse([1, 2])).toEqual({ ok: false, error: 'El mensaje debe ser un objeto' });
    expect(parseClientMessage('{roto', 1000)).toEqual({ ok: false, error: 'JSON inválido' });
    expect(parseClientMessage(JSON.stringify({ type: 'ping', pad: 'x'.repeat(200) }), 100)).toEqual({ ok: false, error: 'Mensaje demasiado grande' });
  });
});
