import { describe, expect, it } from 'vitest';
import { ICU_BEDS, classify, pharmacyNotice, sampleVitals } from '../src/sse/vitals.js';

describe('signos vitales sintéticos de UCI', () => {
  it('son deterministas por cama y tick, y se mantienen en rangos fisiológicos', () => {
    const bed = ICU_BEDS[0]!;
    const date = new Date('2026-11-15T10:00:00Z');
    expect(sampleVitals(bed, 42, date)).toEqual(sampleVitals(bed, 42, date));

    for (let tick = 0; tick < 300; tick++) {
      for (const each of ICU_BEDS) {
        const sample = sampleVitals(each, tick, date);
        expect(sample.heartRate).toBeGreaterThan(40);
        expect(sample.heartRate).toBeLessThan(170);
        expect(sample.spo2).toBeLessThanOrEqual(100);
        expect(sample.spo2).toBeGreaterThan(75);
        expect(sample.systolic).toBeGreaterThan(sample.diastolic);
      }
    }
  });

  it('la cama UCI-03 tiene episodios periódicos de desaturación que disparan alarma crítica', () => {
    const bed = ICU_BEDS.find((each) => each.bedId === 'UCI-03')!;
    const levels = Array.from({ length: 90 }, (_, tick) => sampleVitals(bed, tick).alarm);
    expect(levels.slice(80)).toContain('CRITICAL');
    expect(levels.slice(0, 60)).not.toContain('CRITICAL');
  });

  it('clasifica alarmas por umbrales clínicos', () => {
    expect(classify({ heartRate: 80, spo2: 97, systolic: 120, respRate: 16, temperature: 36.8 })).toEqual({ alarm: 'NORMAL', reasons: [] });
    expect(classify({ heartRate: 115, spo2: 90, systolic: 120, respRate: 16, temperature: 36.8 })).toEqual({
      alarm: 'WARNING',
      reasons: ['SpO₂ 90%', 'FC 115 lpm'],
    });
    expect(classify({ heartRate: 80, spo2: 85, systolic: 80, respRate: 30, temperature: 39 }).alarm).toBe('CRITICAL');
  });

  it('genera avisos de farmacia rotando camas y medicamentos', () => {
    const notices = Array.from({ length: 12 }, (_, tick) => pharmacyNotice(tick));
    expect(new Set(notices.map((notice) => notice.bedId)).size).toBe(ICU_BEDS.length);
    expect(notices[3]!.noticeId).toBe('FAR-000003');
    expect(notices.map((notice) => notice.status)).toContain('SIN_STOCK');
  });
});
