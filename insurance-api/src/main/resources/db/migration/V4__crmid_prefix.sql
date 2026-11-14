UPDATE patients SET crm_id = 'CRMID-' || substr(crm_id, 5) WHERE crm_id LIKE 'CRM-%';
