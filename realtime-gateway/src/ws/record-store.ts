import type { Redis } from 'ioredis';
import { RECORD_FIELDS, type RecordField } from './protocol.js';

export interface LockOwner {
  userId: string;
  email: string;
  connectionId: string;
}

export interface RecordLock extends LockOwner {
  recordId: string;
  acquiredAt: number;
  expiresAt: number;
}

export interface ClinicalRecord {
  recordId: string;
  version: number;
  fields: Record<RecordField, string>;
  updatedAt: string;
  updatedBy: string | null;
}

export type LockResult = { acquired: true; lock: RecordLock } | { acquired: false; lock: RecordLock };
export type EditResult =
  | { ok: true; record: ClinicalRecord }
  | { ok: false; reason: 'NOT_LOCKED' | 'STALE'; record: ClinicalRecord; lock: RecordLock | null };

export interface RecordStore {
  readonly kind: 'memory' | 'redis';
  get(recordId: string): Promise<ClinicalRecord>;
  currentLock(recordId: string): Promise<RecordLock | null>;
  acquireLock(recordId: string, owner: LockOwner, ttlMs: number): Promise<LockResult>;
  releaseLock(recordId: string, connectionId: string): Promise<boolean>;
  releaseByConnection(connectionId: string): Promise<string[]>;
  applyEdit(recordId: string, field: RecordField, value: string, baseVersion: number, owner: LockOwner): Promise<EditResult>;
}

export function blankRecord(recordId: string, now = new Date()): ClinicalRecord {
  return {
    recordId,
    version: 0,
    fields: {
      motivoConsulta: '',
      diagnostico: '',
      evolucion: '',
      plan: '',
    },
    updatedAt: now.toISOString(),
    updatedBy: null,
  };
}

export class MemoryRecordStore implements RecordStore {
  readonly kind = 'memory' as const;
  private readonly records = new Map<string, ClinicalRecord>();
  private readonly locks = new Map<string, RecordLock>();

  constructor(private readonly clock: () => number = Date.now) {}

  async get(recordId: string): Promise<ClinicalRecord> {
    let record = this.records.get(recordId);
    if (!record) {
      record = blankRecord(recordId, new Date(this.clock()));
      this.records.set(recordId, record);
    }
    return structuredClone(record);
  }

  async currentLock(recordId: string): Promise<RecordLock | null> {
    const lock = this.locks.get(recordId);
    if (!lock) return null;
    if (lock.expiresAt <= this.clock()) {
      this.locks.delete(recordId);
      return null;
    }
    return { ...lock };
  }

  async acquireLock(recordId: string, owner: LockOwner, ttlMs: number): Promise<LockResult> {
    const now = this.clock();
    const existing = await this.currentLock(recordId);
    if (existing && existing.connectionId !== owner.connectionId) return { acquired: false, lock: existing };
    const lock: RecordLock = { recordId, ...owner, acquiredAt: existing?.acquiredAt ?? now, expiresAt: now + ttlMs };
    this.locks.set(recordId, lock);
    return { acquired: true, lock: { ...lock } };
  }

  async releaseLock(recordId: string, connectionId: string): Promise<boolean> {
    const lock = this.locks.get(recordId);
    if (!lock || lock.connectionId !== connectionId) return false;
    this.locks.delete(recordId);
    return true;
  }

  async releaseByConnection(connectionId: string): Promise<string[]> {
    const released: string[] = [];
    for (const [recordId, lock] of this.locks) {
      if (lock.connectionId === connectionId) {
        this.locks.delete(recordId);
        released.push(recordId);
      }
    }
    return released;
  }

  async applyEdit(recordId: string, field: RecordField, value: string, baseVersion: number, owner: LockOwner): Promise<EditResult> {
    const record = await this.get(recordId);
    const lock = await this.currentLock(recordId);
    if (!lock || lock.connectionId !== owner.connectionId) return { ok: false, reason: 'NOT_LOCKED', record, lock };
    if (record.version !== baseVersion) return { ok: false, reason: 'STALE', record, lock };
    const updated: ClinicalRecord = {
      ...record,
      version: record.version + 1,
      fields: { ...record.fields, [field]: value },
      updatedAt: new Date(this.clock()).toISOString(),
      updatedBy: owner.email,
    };
    this.records.set(recordId, updated);
    return { ok: true, record: structuredClone(updated) };
  }
}

const RELEASE_SCRIPT = `
local current = redis.call('GET', KEYS[1])
if not current then return 0 end
if cjson.decode(current).connectionId ~= ARGV[1] then return 0 end
redis.call('DEL', KEYS[1])
redis.call('SREM', KEYS[2], ARGV[2])
return 1`;

const EDIT_SCRIPT = `
local lock = redis.call('GET', KEYS[1])
local raw = redis.call('GET', KEYS[2])
local record
if raw then record = cjson.decode(raw) else record = cjson.decode(ARGV[5]) end
if not lock or cjson.decode(lock).connectionId ~= ARGV[1] then
  return {'NOT_LOCKED', cjson.encode(record), lock or ''}
end
if tonumber(record.version) ~= tonumber(ARGV[2]) then
  return {'STALE', cjson.encode(record), lock}
end
record.fields[ARGV[3]] = ARGV[4]
record.version = record.version + 1
record.updatedAt = ARGV[6]
record.updatedBy = ARGV[7]
local encoded = cjson.encode(record)
redis.call('SET', KEYS[2], encoded)
return {'OK', encoded, lock}`;

const recordKey = (recordId: string) => `erp:hc:${recordId}`;
const lockKey = (recordId: string) => `erp:hc-lock:${recordId}`;
const connectionKey = (connectionId: string) => `erp:hc-locks-by-conn:${connectionId}`;

function normalize(record: ClinicalRecord): ClinicalRecord {
  const fields = { ...blankRecord(record.recordId).fields };
  for (const field of RECORD_FIELDS) {
    const value = (record.fields as Partial<Record<RecordField, unknown>>)[field];
    fields[field] = typeof value === 'string' ? value : '';
  }
  return { ...record, fields, version: Number(record.version) };
}

export class RedisRecordStore implements RecordStore {
  readonly kind = 'redis' as const;

  constructor(private readonly redis: Redis) {}

  async get(recordId: string): Promise<ClinicalRecord> {
    const raw = await this.redis.get(recordKey(recordId));
    if (raw) return normalize(JSON.parse(raw) as ClinicalRecord);
    const record = blankRecord(recordId);
    await this.redis.set(recordKey(recordId), JSON.stringify(record), 'NX');
    return normalize(JSON.parse((await this.redis.get(recordKey(recordId))) ?? JSON.stringify(record)) as ClinicalRecord);
  }

  async currentLock(recordId: string): Promise<RecordLock | null> {
    const raw = await this.redis.get(lockKey(recordId));
    return raw ? (JSON.parse(raw) as RecordLock) : null;
  }

  async acquireLock(recordId: string, owner: LockOwner, ttlMs: number): Promise<LockResult> {
    const now = Date.now();
    const lock: RecordLock = { recordId, ...owner, acquiredAt: now, expiresAt: now + ttlMs };
    const created = await this.redis.set(lockKey(recordId), JSON.stringify(lock), 'PX', ttlMs, 'NX');
    if (created === 'OK') {
      await this.redis.sadd(connectionKey(owner.connectionId), recordId);
      await this.redis.pexpire(connectionKey(owner.connectionId), ttlMs * 2);
      return { acquired: true, lock };
    }
    const existing = await this.currentLock(recordId);
    if (existing && existing.connectionId === owner.connectionId) {
      const refreshed = { ...existing, expiresAt: now + ttlMs };
      await this.redis.set(lockKey(recordId), JSON.stringify(refreshed), 'PX', ttlMs, 'XX');
      return { acquired: true, lock: refreshed };
    }
    if (!existing) return this.acquireLock(recordId, owner, ttlMs);
    return { acquired: false, lock: existing };
  }

  async releaseLock(recordId: string, connectionId: string): Promise<boolean> {
    const released = await this.redis.eval(RELEASE_SCRIPT, 2, lockKey(recordId), connectionKey(connectionId), connectionId, recordId);
    return released === 1;
  }

  async releaseByConnection(connectionId: string): Promise<string[]> {
    const recordIds = await this.redis.smembers(connectionKey(connectionId));
    const released: string[] = [];
    for (const recordId of recordIds) {
      if (await this.releaseLock(recordId, connectionId)) released.push(recordId);
    }
    await this.redis.del(connectionKey(connectionId));
    return released;
  }

  async applyEdit(recordId: string, field: RecordField, value: string, baseVersion: number, owner: LockOwner): Promise<EditResult> {
    const result = (await this.redis.eval(EDIT_SCRIPT, 2, lockKey(recordId), recordKey(recordId), owner.connectionId, String(baseVersion),
      field, value, JSON.stringify(blankRecord(recordId)), new Date().toISOString(), owner.email)) as [string, string, string];
    const [status, encoded, rawLock] = result;
    const record = normalize(JSON.parse(encoded) as ClinicalRecord);
    const lock = rawLock ? (JSON.parse(rawLock) as RecordLock) : null;
    if (status === 'OK') return { ok: true, record };
    return { ok: false, reason: status === 'STALE' ? 'STALE' : 'NOT_LOCKED', record, lock };
  }
}
