ALTER TABLE patients ADD COLUMN city TEXT;
ALTER TABLE patients ADD COLUMN crm_id VARCHAR(16);
UPDATE patients SET crm_id = 'CRM-' || upper(substr(replace(id::text, '-', ''), 1, 8)) WHERE crm_id IS NULL;
ALTER TABLE patients ALTER COLUMN crm_id SET NOT NULL;
ALTER TABLE patients ADD CONSTRAINT uk_patients_crm_id UNIQUE (crm_id);
