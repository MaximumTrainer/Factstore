-- Store pipe-separated previous names to support flow rename with forwarding.
ALTER TABLE flows ADD COLUMN previous_names TEXT NULL;
