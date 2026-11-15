import WebSocket from 'ws';

export interface HubMessage {
  type: string;
  [key: string]: unknown;
}

export class TestClient {
  readonly messages: HubMessage[] = [];
  private waiters: Array<{ predicate: (message: HubMessage) => boolean; resolve: (message: HubMessage) => void }> = [];

  private constructor(readonly socket: WebSocket) {
    socket.on('message', (raw) => {
      const message = JSON.parse(raw.toString()) as HubMessage;
      this.messages.push(message);
      this.waiters = this.waiters.filter((waiter) => {
        if (!waiter.predicate(message)) return true;
        waiter.resolve(message);
        return false;
      });
    });
  }

  static async connect(base: string, token: string, query = '', protocols = true): Promise<TestClient> {
    const url = `${base.replace('http', 'ws')}/ws/medical-hub${query ? `?${query}` : ''}`;
    const socket = new WebSocket(url, protocols ? ['bearer', token] : undefined);
    const client = new TestClient(socket);
    await new Promise<void>((resolve, reject) => {
      socket.once('open', () => resolve());
      socket.once('error', reject);
      socket.once('unexpected-response', (_request, response) => reject(new Error(`HTTP ${response.statusCode}`)));
    });
    await client.next('welcome');
    return client;
  }

  send(message: Record<string, unknown>): void {
    this.socket.send(JSON.stringify(message));
  }

  next(type: string, predicate: (message: HubMessage) => boolean = () => true, timeoutMs = 3000): Promise<HubMessage> {
    const existing = this.messages.find((message) => message.type === type && predicate(message));
    if (existing) {
      this.messages.splice(this.messages.indexOf(existing), 1);
      return Promise.resolve(existing);
    }
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error(`No llegó ${type} en ${timeoutMs} ms`)), timeoutMs);
      this.waiters.push({
        predicate: (message) => message.type === type && predicate(message),
        resolve: (message) => {
          clearTimeout(timer);
          this.messages.splice(this.messages.indexOf(message), 1);
          resolve(message);
        },
      });
    });
  }

  received(type: string): HubMessage[] {
    return this.messages.filter((message) => message.type === type);
  }

  close(): Promise<void> {
    if (this.socket.readyState === WebSocket.CLOSED) return Promise.resolve();
    return new Promise((resolve) => {
      this.socket.once('close', () => resolve());
      this.socket.close();
    });
  }
}
