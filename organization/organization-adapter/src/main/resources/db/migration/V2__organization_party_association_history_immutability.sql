-- Association history is an audit trail, not a mutable projection.
-- Restrict UPDATE, DELETE and TRUNCATE even if an application role is misconfigured.
CREATE OR REPLACE FUNCTION organization_party_association_history_immutable()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'organization_party_association_history is append-only'
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER organization_party_association_history_no_update_delete
    BEFORE UPDATE OR DELETE ON organization_party_association_history
    FOR EACH ROW EXECUTE FUNCTION organization_party_association_history_immutable();

CREATE TRIGGER organization_party_association_history_no_truncate
    BEFORE TRUNCATE ON organization_party_association_history
    FOR EACH STATEMENT EXECUTE FUNCTION organization_party_association_history_immutable();
