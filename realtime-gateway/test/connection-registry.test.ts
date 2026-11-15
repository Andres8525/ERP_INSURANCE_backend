import { describe, expect, it } from 'vitest';
import { ConnectionRegistry } from '../src/ws/connection-registry.js';
import { FakeSocket, principal } from './fakes.js';

describe('ConnectionRegistry', () => {
  it('asocia cada socket con usuario, rol y sede y resuelve destinos por índice', () => {
    const registry = new ConnectionRegistry();
    const doctor = registry.add(new FakeSocket(), principal('medico-1'), '10.0.0.5');
    const doctorTablet = registry.add(new FakeSocket(), principal('medico-1'));
    const nurse = registry.add(new FakeSocket(), principal('enfermera-1', { clinicalRole: 'ENFERMERO', sedeId: 'SEDE-ORL' }));
    registry.join(doctor.connectionId, 'tele-1');
    registry.join(nurse.connectionId, 'tele-1');

    expect(doctor.remoteAddress).toBe('10.0.0.5');
    expect(registry.resolve({ kind: 'user', userId: 'medico-1' }).map((each) => each.connectionId).sort())
      .toEqual([doctor.connectionId, doctorTablet.connectionId].sort());
    expect(registry.resolve({ kind: 'sede', sedeId: 'SEDE-ORL' })).toEqual([nurse]);
    expect(registry.resolve({ kind: 'role', role: 'MEDICO' })).toHaveLength(2);
    expect(registry.resolve({ kind: 'room', roomId: 'tele-1' })).toHaveLength(2);
    expect(registry.resolve({ kind: 'all' })).toHaveLength(3);
    expect(registry.resolve({ kind: 'user', userId: 'nadie' })).toEqual([]);
    expect(registry.snapshot()).toEqual({
      connections: 3,
      users: 2,
      rooms: 1,
      bySede: { 'SEDE-MIA': 2, 'SEDE-ORL': 1 },
      byRole: { MEDICO: 2, ENFERMERO: 1 },
    });
  });

  it('al quitar una conexión limpia todos los índices para no dejar fugas de memoria', () => {
    const registry = new ConnectionRegistry();
    const sockets = Array.from({ length: 1000 }, (_, i) => registry.add(new FakeSocket(), principal(`u-${i % 50}`, { sedeId: i % 2 ? 'SEDE-ORL' : 'SEDE-MIA' })));
    for (const entry of sockets) registry.join(entry.connectionId, `sala-${entry.principal.userId}`);
    for (const entry of sockets) registry.remove(entry.connectionId);

    expect(registry.size).toBe(0);
    expect(registry.snapshot()).toEqual({ connections: 0, users: 0, rooms: 0, bySede: {}, byRole: {} });
    expect(registry.remove(sockets[0]!.connectionId)).toBeNull();
  });

  it('gestiona salas, presencia y última actividad', () => {
    let now = 1000;
    const registry = new ConnectionRegistry(() => now);
    const entry = registry.add(new FakeSocket(), principal('medico-1'));
    entry.isAlive = false;
    now = 5000;
    registry.touch(entry.connectionId);
    expect(entry).toMatchObject({ isAlive: true, lastSeenAt: 5000, connectedAt: 1000 });

    expect(registry.join('desconocida', 'sala')).toBe(false);
    registry.join(entry.connectionId, 'sala');
    expect(registry.membersOf('sala')).toEqual([entry]);
    registry.leave(entry.connectionId, 'sala');
    expect(registry.membersOf('sala')).toEqual([]);
    expect(registry.isOnline('medico-1')).toBe(true);
  });
});
