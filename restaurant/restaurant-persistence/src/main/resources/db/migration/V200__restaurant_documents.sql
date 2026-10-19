-- Restaurant aggregates stored as versioned JSONB documents (snapshots), one row per aggregate.
-- Why documents: aggregates are consistency boundaries read and written whole; the few fields queries need are
-- lifted into indexed columns (venue_id, ref_id, starts_at, status).
-- Tenant isolation is enforced by the database (row-level security), not just by WHERE clauses in code.

CREATE TABLE IF NOT EXISTS restaurant_document (
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

CREATE INDEX IF NOT EXISTS restaurant_document_time_idx
    ON restaurant_document (tenant_id, kind, venue_id, starts_at);
CREATE INDEX IF NOT EXISTS restaurant_document_status_idx
    ON restaurant_document (tenant_id, kind, venue_id, status) WHERE status IS NOT NULL;
CREATE INDEX IF NOT EXISTS restaurant_document_ref_idx
    ON restaurant_document (tenant_id, kind, ref_id) WHERE ref_id IS NOT NULL;

-- Row-level security: a connection only sees/writes rows of the tenant set with
--   SELECT set_config('app.tenant_id', '<uuid>', true)    -- true = local to the transaction
-- With no tenant set, current_setting yields '' -> NULL -> no rows match. Fails closed.
ALTER TABLE restaurant_document ENABLE ROW LEVEL SECURITY;
ALTER TABLE restaurant_document FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS restaurant_document_tenant ON restaurant_document;
CREATE POLICY restaurant_document_tenant ON restaurant_document
    USING      (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
