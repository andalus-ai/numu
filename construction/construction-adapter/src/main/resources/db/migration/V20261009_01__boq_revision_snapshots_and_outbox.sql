-- PostgreSQL migration starter for immutable BOQ revisions.
-- Intentionally does not add a foreign key to the existing BOQ table because
-- the source dump does not establish its physical table name/schema.

CREATE TABLE IF NOT EXISTS construction_boq_revision_snapshot (
    boq_id UUID NOT NULL,
    project_id UUID NOT NULL,
    revision_number INTEGER NOT NULL CHECK (revision_number >= 1),
    boq_name TEXT NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('DRAFT', 'UNDER_REVIEW', 'APPROVED', 'SUPERSEDED')),
    calculation_policy_version VARCHAR(128) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    submitted_by UUID NULL,
    submitted_at TIMESTAMPTZ NULL,
    approved_by UUID NULL,
    approved_at TIMESTAMPTZ NULL,
    total_amount NUMERIC(24, 8) NOT NULL DEFAULT 0,
    PRIMARY KEY (boq_id, revision_number),
    CHECK ((status NOT IN ('UNDER_REVIEW', 'APPROVED')) OR (submitted_by IS NOT NULL AND submitted_at IS NOT NULL)),
    CHECK ((status <> 'APPROVED') OR (approved_by IS NOT NULL AND approved_at IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS idx_boq_revision_project
    ON construction_boq_revision_snapshot (project_id, boq_id, revision_number DESC);

CREATE TABLE IF NOT EXISTS construction_boq_revision_item (
    boq_id UUID NOT NULL,
    revision_number INTEGER NOT NULL,
    item_id UUID NOT NULL,
    wbs_node_id UUID NOT NULL,
    item_code VARCHAR(128) NOT NULL,
    description TEXT NOT NULL,
    item_type VARCHAR(32) NOT NULL,
    quantity NUMERIC(24, 8) NOT NULL CHECK (quantity >= 0),
    quantity_unit VARCHAR(64) NOT NULL,
    rate_amount NUMERIC(24, 8) NOT NULL CHECK (rate_amount >= 0),
    rate_unit VARCHAR(64) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    line_amount NUMERIC(24, 8) NOT NULL CHECK (line_amount >= 0),
    PRIMARY KEY (boq_id, revision_number, item_id),
    CONSTRAINT fk_boq_revision_item_header
        FOREIGN KEY (boq_id, revision_number)
        REFERENCES construction_boq_revision_snapshot (boq_id, revision_number)
        ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_boq_revision_item_code
    ON construction_boq_revision_item (boq_id, revision_number, item_code);

-- Minimal transactional outbox contract. The application inserts the event in
-- the same transaction as the revision transition; a worker publishes it later.
CREATE TABLE IF NOT EXISTS construction_outbox_event (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PUBLISHING', 'PUBLISHED', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ NULL,
    last_error TEXT NULL
);

CREATE INDEX IF NOT EXISTS idx_construction_outbox_pending
    ON construction_outbox_event (available_at, occurred_at)
    WHERE status IN ('PENDING', 'FAILED');
