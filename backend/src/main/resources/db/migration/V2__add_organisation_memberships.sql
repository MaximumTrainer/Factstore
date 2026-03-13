-- V2: Organisation membership table for RBAC
CREATE TABLE organisation_memberships (
    id        UUID        NOT NULL,
    org_slug  VARCHAR(255) NOT NULL,
    user_id   UUID        NOT NULL,
    role      VARCHAR(50) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_org_memberships PRIMARY KEY (id),
    CONSTRAINT uq_org_memberships_org_user UNIQUE (org_slug, user_id),
    CONSTRAINT fk_org_memberships_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
