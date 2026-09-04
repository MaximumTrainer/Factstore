-- #156 FR-3: sessions are server-side records, not just signed tokens.
--
-- The session token carries a `jti`; this table is what makes revocation immediate
-- (FR-3.3), sessions listable and individually revocable (FR-3.5, FR-3.6), and idle and
-- absolute timeouts enforceable (FR-3.4). A signed token alone can do none of those:
-- once issued it is valid until it expires, with no way to cut it off.
CREATE TABLE IF NOT EXISTS user_sessions (
    id                  UUID         NOT NULL,
    jti                 VARCHAR(64)  NOT NULL,
    user_id             UUID         NOT NULL,
    org_slug            VARCHAR(255),
    provider            VARCHAR(32)  NOT NULL,
    created_at          TIMESTAMP    NOT NULL,
    last_seen_at        TIMESTAMP    NOT NULL,
    -- When the current token stops being accepted; moves forward on refresh.
    expires_at          TIMESTAMP    NOT NULL,
    -- The ceiling refresh cannot pass, so a session cannot be renewed indefinitely.
    absolute_expires_at TIMESTAMP    NOT NULL,
    revoked_at          TIMESTAMP,
    revoked_reason      VARCHAR(64),
    source_ip           VARCHAR(64),
    user_agent          VARCHAR(512),
    CONSTRAINT pk_user_sessions PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_sessions_jti ON user_sessions (jti);
CREATE INDEX IF NOT EXISTS ix_user_sessions_user ON user_sessions (user_id);
CREATE INDEX IF NOT EXISTS ix_user_sessions_expires ON user_sessions (expires_at);
