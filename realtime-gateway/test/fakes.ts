import type { Principal } from '../src/auth/principal.js';
import type { SocketLike } from '../src/ws/connection-registry.js';

export class FakeSocket implements SocketLike {
  readyState = 1;
  bufferedAmount = 0;
  sent: string[] = [];
  pings = 0;
  terminated = false;
  closedWith: { code?: number; reason?: string } | null = null;

  send(data: string, callback?: (error?: Error) => void): void {
    this.sent.push(data);
    callback?.();
  }

  ping(): void {
    this.pings += 1;
  }

  terminate(): void {
    this.terminated = true;
    this.readyState = 3;
  }

  close(code?: number, reason?: string): void {
    this.closedWith = { code, reason };
    this.readyState = 3;
  }

  messages<T = Record<string, unknown>>(): T[] {
    return this.sent.map((raw) => JSON.parse(raw) as T);
  }
}

export function principal(userId: string, overrides: Partial<Principal> = {}): Principal {
  return { userId, email: `${userId}@nexo.demo`, systemRole: 'ADMIN', clinicalRole: 'MEDICO', sedeId: 'SEDE-MIA', ...overrides };
}
