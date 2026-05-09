-- Add soft-delete archive support to flows.
-- Also drop the unique constraint on name so archived flow names can be reused;
-- uniqueness for active flows is enforced at the application layer.
ALTER TABLE flows DROP CONSTRAINT IF EXISTS uq_flows_name;
ALTER TABLE flows ADD COLUMN archived_at TIMESTAMP WITH TIME ZONE NULL;
