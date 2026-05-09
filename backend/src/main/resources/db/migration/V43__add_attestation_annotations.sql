CREATE TABLE IF NOT EXISTS attestation_annotations (
    attestation_id UUID NOT NULL,
    annotation_key VARCHAR(255) NOT NULL,
    annotation_value VARCHAR(1024),
    PRIMARY KEY (attestation_id, annotation_key)
);
