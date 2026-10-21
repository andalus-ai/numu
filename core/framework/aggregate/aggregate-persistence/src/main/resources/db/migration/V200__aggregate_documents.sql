-- Restaurant aggregates stored as versioned JSONB documents (snapshots), one row per aggregate.
-- Why documents: aggregates are consistency boundaries read and written whole; the few fields queries need are
-- lifted into indexed columns (venue_id, ref_id, starts_at, status).
-- Tenant isolation is enforced by the database (row-level security), not just by WHERE clauses in code.

CREATE TABLE IF NOT EXISTS aggregate_document (
    tenant_id   uuid        NOT NULL,
    kind        text        NOT NULL,                 -- 'reservation', 'ledger', 'ticket', 'order', 'menu', ...
    id          uuid        NOT NULL,
    version     bigint      NOT NULL CHECK (version > 0),
    venue_id    uuid,
    ref_id      uuid,                                 -- secondary key, e.g. ticket -> order id
    starts_at   timestamptz,                          -- time index: reservation start, ticket fired-at, order created-at
    status      text,
    doc         jsonb       NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, kind, id)
);

CREATE INDEX IF NOT EXISTS aggregate_document_time_idx
    ON aggregate_document (tenant_id, kind, venue_id, starts_at);
CREATE INDEX IF NOT EXISTS aggregate_document_status_idx
    ON aggregate_document (tenant_id, kind, venue_id, status) WHERE status IS NOT NULL;
CREATE INDEX IF NOT EXISTS aggregate_document_ref_idx
    ON aggregate_document (tenant_id, kind, ref_id) WHERE ref_id IS NOT NULL;

-- Row-level security: a connection only sees/writes rows of the tenant set with
--   SELECT set_config('app.tenant_id', '<uuid>', true)    -- true = local to the transaction
-- With no tenant set, current_setting yields '' -> NULL -> no rows match. Fails closed.
ALTER TABLE aggregate_document ENABLE ROW LEVEL SECURITY;
ALTER TABLE aggregate_document FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS aggregate_document_tenant ON aggregate_document;
CREATE POLICY aggregate_document_tenant ON aggregate_document
    USING      (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
