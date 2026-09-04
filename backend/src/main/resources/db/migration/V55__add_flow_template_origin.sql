-- #162: record which template a flow was created from, so drift against that template can be
-- reported. The template is copied at creation, never linked live, so a template update can
-- never silently change what an in-flight release is judged against.
ALTER TABLE flows ADD COLUMN IF NOT EXISTS template_id VARCHAR(128);
ALTER TABLE flows ADD COLUMN IF NOT EXISTS template_version VARCHAR(32);
