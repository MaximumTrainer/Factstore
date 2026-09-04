-- #161: soft delete for trails. Trails are compliance evidence, so the default way to
-- retire one is to archive it: the record survives, it just stops cluttering the listings.
ALTER TABLE trails ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS ix_trails_archived_at ON trails (archived_at);
