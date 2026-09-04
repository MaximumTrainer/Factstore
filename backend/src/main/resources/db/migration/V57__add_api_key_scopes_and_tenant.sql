-- #155 FR-3, FR-5, FR-6: least privilege and tenant binding for machine credentials.
--
-- Every valid key previously granted the single authority ROLE_API_USER, so a CI key that
-- only needed to post attestations could delete flows, mint further keys, and read every
-- organisation's evidence. Scopes and an organisation binding fix that.
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS scopes TEXT;

-- A key authenticates into one organisation. Null means "not bound", which is the
-- single-tenant case and the state every pre-existing key is in.
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS org_slug VARCHAR(255);

-- Rotation (FR-6.1): the replaced key keeps working for an overlap window so a pipeline can
-- roll without an outage, and the pair is traceable.
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS rotated_from_id UUID;
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS superseded_at TIMESTAMP;
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS overlap_expires_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS ix_api_keys_org_slug ON api_keys (org_slug);
CREATE INDEX IF NOT EXISTS ix_api_keys_expires_at ON api_keys (expires_at);
