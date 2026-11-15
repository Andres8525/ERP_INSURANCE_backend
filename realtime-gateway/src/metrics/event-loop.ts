import { monitorEventLoopDelay, type IntervalHistogram } from 'node:perf_hooks';

export interface EventLoopSnapshot {
  meanMs: number;
  p99Ms: number;
  maxMs: number;
}

const toMs = (nanoseconds: number) => Math.round((nanoseconds / 1e6) * 100) / 100;

export class EventLoopMonitor {
  private readonly histogram: IntervalHistogram;

  constructor(resolutionMs = 10) {
    this.histogram = monitorEventLoopDelay({ resolution: resolutionMs });
    this.histogram.enable();
  }

  snapshot(reset = false): EventLoopSnapshot {
    const snapshot = {
      meanMs: Number.isFinite(this.histogram.mean) ? toMs(this.histogram.mean) : 0,
      p99Ms: toMs(this.histogram.percentile(99)),
      maxMs: toMs(this.histogram.max),
    };
    if (reset) this.histogram.reset();
    return snapshot;
  }

  stop(): void {
    this.histogram.disable();
  }
}
