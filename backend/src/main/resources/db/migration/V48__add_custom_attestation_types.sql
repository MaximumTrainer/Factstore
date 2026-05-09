CREATE TABLE IF NOT EXISTS custom_attestation_types (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT NOT NULL DEFAULT '',
    version INT NOT NULL DEFAULT 1,
    org_slug VARCHAR(255),
    archived_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
