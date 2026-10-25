-- V5: persist provider-side attributes captured on federated identity links.
-- Keep the migration additive so existing deployments retain all linked identities.
ALTER TABLE federated_identity
    ADD COLUMN IF NOT EXISTS attributes TEXT;
