ALTER TABLE flows ADD COLUMN requires_approval BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE flows ADD COLUMN required_approver_roles TEXT;

CREATE TABLE approvals (
    id                    UUID        NOT NULL,
    trail_id              UUID        NOT NULL,
    flow_id               UUID        NOT NULL,
    status                VARCHAR(50) NOT NULL DEFAULT 'PENDING_APPROVAL',
    required_approvers    TEXT,
    comments              TEXT,
    requested_at          TIMESTAMP   NOT NULL,
    deadline              TIMESTAMP,
    resolved_at           TIMESTAMP,
    CONSTRAINT pk_approvals PRIMARY KEY (id),
    CONSTRAINT fk_approvals_trail FOREIGN KEY (trail_id) REFERENCES trails (id) ON DELETE CASCADE
);

CREATE TABLE approval_decisions (
    id                UUID        NOT NULL,
    approval_id       UUID        NOT NULL,
    approver_identity VARCHAR(255) NOT NULL,
    decision          VARCHAR(50) NOT NULL,
    comments          TEXT,
    decided_at        TIMESTAMP   NOT NULL,
    CONSTRAINT pk_approval_decisions PRIMARY KEY (id),
    CONSTRAINT fk_approval_decisions_approval FOREIGN KEY (approval_id) REFERENCES approvals (id) ON DELETE CASCADE
);
