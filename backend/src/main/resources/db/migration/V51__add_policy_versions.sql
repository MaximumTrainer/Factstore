CREATE TABLE IF NOT EXISTS policy_versions (
    id UUID NOT NULL PRIMARY KEY,
    policy_id UUID NOT NULL,
    version INT NOT NULL,
    content TEXT NOT NULL,
    change_comment TEXT,
    created_at TIMESTAMP NOT NULL
);

ALTER TABLE policies ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1;
