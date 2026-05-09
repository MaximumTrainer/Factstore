ALTER TABLE attestations ADD COLUMN IF NOT EXISTS artifact_id UUID;
ALTER TABLE attestations ADD COLUMN IF NOT EXISTS overrides_attestation_id UUID;
ALTER TABLE attestations ADD COLUMN IF NOT EXISTS justification TEXT;
