import { describe, expect, it } from 'vitest';
import { MemoryRecordStore, blankRecord } from '../src/ws/record-store.js';

const doctor = { userId: 'medico-1', email: 'medico1@nexo.demo', connectionId: 'conn-a' };
const nurse = { userId: 'enfermera-1', email: 'enfermera1@nexo.demo', connectionId: 'conn-b' };

describe('MemoryRecordStore (historia clínica colaborativa)', () => {
  it('crea historias vacías bajo demanda y entrega copias inmutables', async () => {
    const store = new MemoryRecordStore();
    const record = await store.get('HC-1001');
    expect(record).toMatchObject({ recordId: 'HC-1001', version: 0, updatedBy: null });
    record.fields.plan = 'mutado fuera del store';
    expect((await store.get('HC-1001')).fields.plan).toBe('');
    expect(Object.keys(blankRecord('HC-1').fields)).toEqual(['motivoConsulta', 'diagnostico', 'evolucion', 'plan']);
  });

  it('bloqueo exclusivo por conexión, reentrante y con expiración', async () => {
    let now = 1_000;
    const store = new MemoryRecordStore(() => now);
    expect((await store.acquireLock('HC-1', doctor, 60_000)).acquired).toBe(true);

    const denied = await store.acquireLock('HC-1', nurse, 60_000);
    expect(denied).toMatchObject({ acquired: false, lock: { userId: 'medico-1', connectionId: 'conn-a' } });

    now = 30_000;
    const refreshed = await store.acquireLock('HC-1', doctor, 60_000);
    expect(refreshed).toMatchObject({ acquired: true, lock: { acquiredAt: 1_000, expiresAt: 90_000 } });

    now = 91_000;
    expect(await store.currentLock('HC-1')).toBeNull();
    expect((await store.acquireLock('HC-1', nurse, 60_000)).acquired).toBe(true);
  });

  it('solo el dueño del bloqueo edita y se rechazan versiones desactualizadas', async () => {
    const store = new MemoryRecordStore();
    expect(await store.applyEdit('HC-1', 'plan', 'Reposo', 0, doctor)).toMatchObject({ ok: false, reason: 'NOT_LOCKED', lock: null });

    await store.acquireLock('HC-1', doctor, 60_000);
    const first = await store.applyEdit('HC-1', 'diagnostico', 'Neumonía adquirida en la comunidad', 0, doctor);
    expect(first).toMatchObject({ ok: true, record: { version: 1, updatedBy: 'medico1@nexo.demo' } });

    const stale = await store.applyEdit('HC-1', 'plan', 'Antibiótico', 0, doctor);
    expect(stale).toMatchObject({ ok: false, reason: 'STALE', record: { version: 1 } });

    const intruder = await store.applyEdit('HC-1', 'plan', 'Alta', 1, nurse);
    expect(intruder).toMatchObject({ ok: false, reason: 'NOT_LOCKED', lock: { userId: 'medico-1' } });
  });

  it('libera los bloqueos de una conexión al desconectarse', async () => {
    const store = new MemoryRecordStore();
    await store.acquireLock('HC-1', doctor, 60_000);
    await store.acquireLock('HC-2', doctor, 60_000);
    await store.acquireLock('HC-3', nurse, 60_000);
    expect((await store.releaseByConnection('conn-a')).sort()).toEqual(['HC-1', 'HC-2']);
    expect(await store.currentLock('HC-3')).not.toBeNull();
    expect(await store.releaseLock('HC-3', 'conn-a')).toBe(false);
    expect(await store.releaseLock('HC-3', 'conn-b')).toBe(true);
  });
});
