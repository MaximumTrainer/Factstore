CREATE TABLE deployment_policies (
    id                         UUID         NOT NULL,
    name                       VARCHAR(255) NOT NULL,
    description                TEXT,
    environment_id             UUID,
    flow_id                    UUID         NOT NULL,
    enforce_provenance         BOOLEAN      NOT NULL DEFAULT FALSE,
    enforce_approvals          BOOLEAN      NOT NULL DEFAULT FALSE,
    required_attestation_types TEXT,
    is_active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                 TIMESTAMPTZ  NOT NULL,
    updated_at                 TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_deployment_policies PRIMARY KEY (id),
    CONSTRAINT fk_deployment_policies_flow FOREIGN KEY (flow_id) REFERENCES flows (id)
);

CREATE TABLE deployment_gate_results (
    id              UUID         NOT NULL,
    policy_id       UUID,
    artifact_sha256 VARCHAR(255) NOT NULL,
    environment_id  UUID,
    requested_by    VARCHAR(255),
    decision        VARCHAR(50)  NOT NULL,
    evaluated_at    TIMESTAMPTZ  NOT NULL,
    block_reasons   TEXT,
    CONSTRAINT pk_deployment_gate_results PRIMARY KEY (id)
);
