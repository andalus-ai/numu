-- Immutable point-in-time snapshots for consultancy deliverable creation and lifecycle transitions.
CREATE TABLE IF NOT EXISTS construction_consultancy_deliverable_history (
    tenant_id VARCHAR(100) NOT NULL,
    sequence_no BIGINT GENERATED ALWAYS AS IDENTITY,
    history_id UUID NOT NULL,
    deliverable_id UUID NOT NULL,
    engagement_id UUID NOT NULL,
    revision INTEGER NOT NULL CHECK (revision >= 1),
    action VARCHAR(24) NOT NULL CHECK (action IN ('CREATED', 'SUBMITTED', 'ACCEPTED', 'REJECTED', 'REVISED')),
    status VARCHAR(24) NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'ACCEPTED', 'REJECTED')),
    title TEXT NOT NULL CHECK (length(btrim(title)) > 0),
    description TEXT NOT NULL CHECK (length(btrim(description)) > 0),
    reviewed_by_organization_id UUID NULL,
    review_comment TEXT NULL,
    actor_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, sequence_no),
    CONSTRAINT uq_construction_consultancy_deliverable_history_id UNIQUE (tenant_id, history_id),
    CONSTRAINT fk_construction_consultancy_deliverable_history_deliverable
        FOREIGN KEY (tenant_id, deliverable_id)
        REFERENCES construction_consultancy_deliverable (tenant_id, deliverable_id),
    CONSTRAINT ck_construction_consultancy_deliverable_history_review CHECK (
        (status IN ('DRAFT', 'SUBMITTED') AND reviewed_by_organization_id IS NULL AND review_comment IS NULL)
        OR (status = 'ACCEPTED' AND reviewed_by_organization_id IS NOT NULL)
        OR (status = 'REJECTED' AND reviewed_by_organization_id IS NOT NULL
            AND review_comment IS NOT NULL AND length(btrim(review_comment)) > 0)
    )
);
CREATE INDEX IF NOT EXISTS idx_construction_consultancy_deliverable_history_timeline
    ON construction_consultancy_deliverable_history (tenant_id, deliverable_id, sequence_no);
CREATE OR REPLACE FUNCTION construction_reject_consultancy_deliverable_history_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Consultancy deliverable history is append-only';
END;
$$;
DROP TRIGGER IF EXISTS trg_construction_consultancy_deliverable_history_immutable
    ON construction_consultancy_deliverable_history;
CREATE TRIGGER trg_construction_consultancy_deliverable_history_immutable
    BEFORE UPDATE OR DELETE ON construction_consultancy_deliverable_history
    FOR EACH ROW EXECUTE FUNCTION construction_reject_consultancy_deliverable_history_mutation();
