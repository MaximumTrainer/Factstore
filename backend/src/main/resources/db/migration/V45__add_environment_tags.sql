CREATE TABLE IF NOT EXISTS environment_tags (
    environment_id UUID NOT NULL,
    tag_key VARCHAR(64) NOT NULL,
    tag_value VARCHAR(256),
    PRIMARY KEY (environment_id, tag_key)
);
