CREATE TABLE scm_integrations (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    org_slug VARCHAR(255) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    token_encrypted TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (org_slug, provider)
);
