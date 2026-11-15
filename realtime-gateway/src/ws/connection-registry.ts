import { randomUUID } from 'node:crypto';
import type { Principal } from '../auth/principal.js';
import type { DeliveryTarget } from '../backplane/backplane.js';

export const OPEN = 1;

export interface SocketLike {
  readyState: number;
  bufferedAmount: number;
  send(data: string, callback?: (error?: Error) => void): void;
  ping(): void;
  terminate(): void;
  close(code?: number, reason?: string): void;
}

export interface AuthenticatedSocket {
  connectionId: string;
  socket: SocketLike;
  principal: Principal;
  remoteAddress: string | null;
  connectedAt: number;
  lastSeenAt: number;
  isAlive: boolean;
  rooms: Set<string>;
  messagesIn: number;
  messagesOut: number;
}

export interface RegistrySnapshot {
  connections: number;
  users: number;
  rooms: number;
  bySede: Record<string, number>;
  byRole: Record<string, number>;
}

function addToIndex(index: Map<string, Set<string>>, key: string, connectionId: string): void {
  let bucket = index.get(key);
  if (!bucket) {
    bucket = new Set();
    index.set(key, bucket);
  }
  bucket.add(connectionId);
}

function removeFromIndex(index: Map<string, Set<string>>, key: string, connectionId: string): void {
  const bucket = index.get(key);
  if (!bucket) return;
  bucket.delete(connectionId);
  if (bucket.size === 0) index.delete(key);
}

export class ConnectionRegistry {
  private readonly connections = new Map<string, AuthenticatedSocket>();
  private readonly byUser = new Map<string, Set<string>>();
  private readonly bySede = new Map<string, Set<string>>();
  private readonly byRole = new Map<string, Set<string>>();
  private readonly byRoom = new Map<string, Set<string>>();

  constructor(private readonly clock: () => number = Date.now) {}

  add(socket: SocketLike, principal: Principal, remoteAddress: string | null = null): AuthenticatedSocket {
    const now = this.clock();
    const entry: AuthenticatedSocket = {
      connectionId: randomUUID(),
      socket,
      principal,
      remoteAddress,
      connectedAt: now,
      lastSeenAt: now,
      isAlive: true,
      rooms: new Set(),
      messagesIn: 0,
      messagesOut: 0,
    };
    this.connections.set(entry.connectionId, entry);
    addToIndex(this.byUser, principal.userId, entry.connectionId);
    addToIndex(this.bySede, principal.sedeId, entry.connectionId);
    addToIndex(this.byRole, principal.clinicalRole, entry.connectionId);
    return entry;
  }

  remove(connectionId: string): AuthenticatedSocket | null {
    const entry = this.connections.get(connectionId);
    if (!entry) return null;
    this.connections.delete(connectionId);
    removeFromIndex(this.byUser, entry.principal.userId, connectionId);
    removeFromIndex(this.bySede, entry.principal.sedeId, connectionId);
    removeFromIndex(this.byRole, entry.principal.clinicalRole, connectionId);
    for (const room of entry.rooms) removeFromIndex(this.byRoom, room, connectionId);
    entry.rooms.clear();
    return entry;
  }

  join(connectionId: string, roomId: string): boolean {
    const entry = this.connections.get(connectionId);
    if (!entry) return false;
    entry.rooms.add(roomId);
    addToIndex(this.byRoom, roomId, connectionId);
    return true;
  }

  leave(connectionId: string, roomId: string): void {
    const entry = this.connections.get(connectionId);
    entry?.rooms.delete(roomId);
    removeFromIndex(this.byRoom, roomId, connectionId);
  }

  touch(connectionId: string): void {
    const entry = this.connections.get(connectionId);
    if (!entry) return;
    entry.lastSeenAt = this.clock();
    entry.isAlive = true;
  }

  get(connectionId: string): AuthenticatedSocket | undefined {
    return this.connections.get(connectionId);
  }

  values(): IterableIterator<AuthenticatedSocket> {
    return this.connections.values();
  }

  get size(): number {
    return this.connections.size;
  }

  membersOf(roomId: string): AuthenticatedSocket[] {
    return this.collect(this.byRoom.get(roomId));
  }

  isOnline(userId: string): boolean {
    return this.byUser.has(userId);
  }

  resolve(target: DeliveryTarget): AuthenticatedSocket[] {
    switch (target.kind) {
      case 'all':
        return [...this.connections.values()];
      case 'user':
        return this.collect(this.byUser.get(target.userId));
      case 'room':
        return this.collect(this.byRoom.get(target.roomId));
      case 'sede':
        return this.collect(this.bySede.get(target.sedeId));
      case 'role':
        return this.collect(this.byRole.get(target.role));
    }
  }

  snapshot(): RegistrySnapshot {
    const count = (index: Map<string, Set<string>>) =>
      Object.fromEntries([...index.entries()].map(([key, bucket]) => [key, bucket.size]));
    return {
      connections: this.connections.size,
      users: this.byUser.size,
      rooms: this.byRoom.size,
      bySede: count(this.bySede),
      byRole: count(this.byRole),
    };
  }

  private collect(ids: Set<string> | undefined): AuthenticatedSocket[] {
    if (!ids) return [];
    const result: AuthenticatedSocket[] = [];
    for (const id of ids) {
      const entry = this.connections.get(id);
      if (entry) result.push(entry);
    }
    return result;
  }
}
