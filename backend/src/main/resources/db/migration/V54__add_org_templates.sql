-- #162: organisations publish their own flow templates alongside the built-in catalogue,
-- so a platform team can define the house standard rather than only consuming ours.
CREATE TABLE IF NOT EXISTS org_templates (
    id            UUID         NOT NULL,
    template_id   VARCHAR(128) NOT NULL,
    org_slug      VARCHAR(255),
    name          VARCHAR(255) NOT NULL,
    description   TEXT         NOT NULL,
    framework     VARCHAR(128) NOT NULL,
    version       VARCHAR(32)  NOT NULL,
    category      VARCHAR(32)  NOT NULL,
    service_type  VARCHAR(64),
    template_yaml TEXT         NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT pk_org_templates PRIMARY KEY (id)
);

-- One template id per organisation; a null org_slug is the single-tenant/global case.
CREATE UNIQUE INDEX IF NOT EXISTS ux_org_templates_slug_template
    ON org_templates (org_slug, template_id);
