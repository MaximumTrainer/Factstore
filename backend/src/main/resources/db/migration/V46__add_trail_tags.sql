CREATE TABLE IF NOT EXISTS trail_tags (
    trail_id UUID NOT NULL,
    tag_key VARCHAR(64) NOT NULL,
    tag_value VARCHAR(256),
    PRIMARY KEY (trail_id, tag_key)
);
