ALTER TABLE custom_attestation_types ADD COLUMN IF NOT EXISTS schema_json TEXT;
ALTER TABLE custom_attestation_types ADD COLUMN IF NOT EXISTS jq_expression TEXT;
