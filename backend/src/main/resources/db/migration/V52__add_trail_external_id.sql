-- #164: a stable, pipeline-supplied release identifier so a secondary pipeline can attach
-- its attestations to the trail the primary pipeline created, without knowing the UUID.
ALTER TABLE trails ADD COLUMN IF NOT EXISTS external_id VARCHAR(255);

-- Unique per flow: trail creation is idempotent for a given release identifier, so a
-- re-run of the primary pipeline cannot fork the evidence for one release.
CREATE UNIQUE INDEX IF NOT EXISTS ux_trails_flow_external_id
    ON trails (flow_id, external_id)
    WHERE external_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_trails_git_commit_sha ON trails (git_commit_sha);
