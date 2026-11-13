-- Campos requeridos por las vistas del MVP (pipeline de siniestros, bandeja de anomalías y afiliación).

ALTER TABLE policies ADD COLUMN plan_name VARCHAR(120);

ALTER TABLE claims ADD COLUMN amount NUMERIC(12, 2) CHECK (amount IS NULL OR amount >= 0);
ALTER TABLE claims ADD COLUMN priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE claims ADD COLUMN location VARCHAR(120);
ALTER TABLE claims ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE INDEX idx_claims_patient_id ON claims(patient_id);

CREATE TABLE claim_status_history (
    id UUID PRIMARY KEY,
    claim_id UUID NOT NULL REFERENCES claims(id) ON DELETE CASCADE,
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    note VARCHAR(255),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_claim_history_claim ON claim_status_history(claim_id, changed_at);

ALTER TABLE data_anomalies ADD COLUMN patient_id UUID REFERENCES patients(id) ON DELETE SET NULL;
ALTER TABLE data_anomalies ADD COLUMN title VARCHAR(160);
ALTER TABLE data_anomalies ADD COLUMN source VARCHAR(40);
ALTER TABLE data_anomalies ADD COLUMN confidence INTEGER CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 100);
ALTER TABLE data_anomalies ADD COLUMN field_label VARCHAR(120);
ALTER TABLE data_anomalies ADD COLUMN current_value TEXT;
ALTER TABLE data_anomalies ADD COLUMN conflicting_value TEXT;
ALTER TABLE data_anomalies ADD COLUMN evidence VARCHAR(500);
CREATE INDEX idx_anomalies_patient_id ON data_anomalies(patient_id);
