-- Transactional per-BOQ revision number allocator.
-- Verify migration ordering/naming against the real project's Flyway conventions.
CREATE TABLE IF NOT EXISTS construction_boq_revision_counter (
    boq_id UUID PRIMARY KEY,
    last_allocated_revision INTEGER NOT NULL CHECK (last_allocated_revision >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
