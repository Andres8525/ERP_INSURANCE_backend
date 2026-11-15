export type AlarmLevel = 'NORMAL' | 'WARNING' | 'CRITICAL';

export interface IcuBed {
  bedId: string;
  sedeId: string;
  patientLabel: string;
  baseline: { heartRate: number; spo2: number; systolic: number; diastolic: number; respRate: number; temperature: number };
}

export interface VitalSample {
  bedId: string;
  sedeId: string;
  patientLabel: string;
  tick: number;
  takenAt: string;
  heartRate: number;
  spo2: number;
  systolic: number;
  diastolic: number;
  respRate: number;
  temperature: number;
  alarm: AlarmLevel;
  alarmReasons: string[];
}

export const ICU_BEDS: IcuBed[] = [
  { bedId: 'UCI-01', sedeId: 'SEDE-MIA', patientLabel: 'Paciente UCI-01 · posoperatorio cardíaco', baseline: { heartRate: 84, spo2: 97, systolic: 124, diastolic: 78, respRate: 16, temperature: 36.9 } },
  { bedId: 'UCI-02', sedeId: 'SEDE-MIA', patientLabel: 'Paciente UCI-02 · neumonía', baseline: { heartRate: 98, spo2: 93, systolic: 118, diastolic: 74, respRate: 22, temperature: 38.1 } },
  { bedId: 'UCI-03', sedeId: 'SEDE-ORL', patientLabel: 'Paciente UCI-03 · EPOC exacerbado', baseline: { heartRate: 92, spo2: 92, systolic: 132, diastolic: 84, respRate: 24, temperature: 37.2 } },
  { bedId: 'UCI-04', sedeId: 'SEDE-ORL', patientLabel: 'Paciente UCI-04 · sepsis en manejo', baseline: { heartRate: 108, spo2: 95, systolic: 98, diastolic: 60, respRate: 20, temperature: 38.4 } },
  { bedId: 'UCI-05', sedeId: 'SEDE-TPA', patientLabel: 'Paciente UCI-05 · trauma', baseline: { heartRate: 76, spo2: 98, systolic: 128, diastolic: 82, respRate: 14, temperature: 36.7 } },
  { bedId: 'UCI-06', sedeId: 'SEDE-TPA', patientLabel: 'Paciente UCI-06 · cetoacidosis', baseline: { heartRate: 102, spo2: 96, systolic: 112, diastolic: 70, respRate: 26, temperature: 37.0 } },
];

const DESATURATION_BED = 'UCI-03';
const DESATURATION_PERIOD = 90;
const DESATURATION_LENGTH = 12;

function noise(seed: number): number {
  const x = Math.sin(seed * 12.9898) * 43758.5453;
  return (x - Math.floor(x)) * 2 - 1;
}

function hash(text: string): number {
  let value = 0;
  for (const char of text) value = (value * 31 + char.charCodeAt(0)) | 0;
  return Math.abs(value);
}

function round(value: number, decimals = 0): number {
  const factor = 10 ** decimals;
  return Math.round(value * factor) / factor;
}

export function classify(sample: Pick<VitalSample, 'heartRate' | 'spo2' | 'systolic' | 'respRate' | 'temperature'>): { alarm: AlarmLevel; reasons: string[] } {
  const critical: string[] = [];
  const warning: string[] = [];
  if (sample.spo2 < 88) critical.push(`SpO₂ ${sample.spo2}%`);
  else if (sample.spo2 < 92) warning.push(`SpO₂ ${sample.spo2}%`);
  if (sample.heartRate > 130 || sample.heartRate < 45) critical.push(`FC ${sample.heartRate} lpm`);
  else if (sample.heartRate > 110 || sample.heartRate < 55) warning.push(`FC ${sample.heartRate} lpm`);
  if (sample.systolic < 85) critical.push(`PAS ${sample.systolic} mmHg`);
  else if (sample.systolic < 95 || sample.systolic > 160) warning.push(`PAS ${sample.systolic} mmHg`);
  if (sample.respRate > 28) warning.push(`FR ${sample.respRate} rpm`);
  if (sample.temperature >= 38.5) warning.push(`T ${sample.temperature} °C`);
  if (critical.length) return { alarm: 'CRITICAL', reasons: [...critical, ...warning] };
  if (warning.length) return { alarm: 'WARNING', reasons: warning };
  return { alarm: 'NORMAL', reasons: [] };
}

export function sampleVitals(bed: IcuBed, tick: number, now: Date = new Date()): VitalSample {
  const seed = hash(bed.bedId) + tick;
  const wave = Math.sin(tick / 9 + hash(bed.bedId));
  const desaturating = bed.bedId === DESATURATION_BED && tick % DESATURATION_PERIOD >= DESATURATION_PERIOD - DESATURATION_LENGTH;
  const base = bed.baseline;
  const values = {
    heartRate: round(base.heartRate + wave * 4 + noise(seed) * 3 + (desaturating ? 30 : 0)),
    spo2: Math.min(100, round(base.spo2 + noise(seed + 7) * 1.2 - (desaturating ? 9 : 0))),
    systolic: round(base.systolic + wave * 5 + noise(seed + 13) * 4),
    diastolic: round(base.diastolic + wave * 3 + noise(seed + 17) * 3),
    respRate: round(base.respRate + noise(seed + 23) * 2 + (desaturating ? 7 : 0)),
    temperature: round(base.temperature + noise(seed + 29) * 0.15, 1),
  };
  const { alarm, reasons } = classify(values);
  return {
    bedId: bed.bedId,
    sedeId: bed.sedeId,
    patientLabel: bed.patientLabel,
    tick,
    takenAt: now.toISOString(),
    ...values,
    alarm,
    alarmReasons: reasons,
  };
}

const MEDICATIONS = [
  'Ceftriaxona 1 g IV',
  'Enoxaparina 40 mg SC',
  'Noradrenalina en infusión',
  'Insulina regular según esquema',
  'Salbutamol nebulizado',
  'Omeprazol 40 mg IV',
];

export interface PharmacyNotice {
  noticeId: string;
  bedId: string;
  sedeId: string;
  medication: string;
  status: 'DISPENSADO' | 'EN_CAMINO' | 'SIN_STOCK';
  issuedAt: string;
}

export function pharmacyNotice(tick: number, now: Date = new Date()): PharmacyNotice {
  const bed = ICU_BEDS[tick % ICU_BEDS.length]!;
  const medication = MEDICATIONS[(tick * 7) % MEDICATIONS.length]!;
  const roll = (tick * 13) % 10;
  return {
    noticeId: `FAR-${String(tick).padStart(6, '0')}`,
    bedId: bed.bedId,
    sedeId: bed.sedeId,
    medication,
    status: roll === 0 ? 'SIN_STOCK' : roll < 4 ? 'EN_CAMINO' : 'DISPENSADO',
    issuedAt: now.toISOString(),
  };
}
