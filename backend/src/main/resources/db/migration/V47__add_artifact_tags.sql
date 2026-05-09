CREATE TABLE IF NOT EXISTS artifact_tags (
    artifact_id UUID NOT NULL,
    tag_key VARCHAR(64) NOT NULL,
    tag_value VARCHAR(256),
    PRIMARY KEY (artifact_id, tag_key)
);
