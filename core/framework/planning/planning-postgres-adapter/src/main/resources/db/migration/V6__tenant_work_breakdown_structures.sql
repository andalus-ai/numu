CREATE TABLE IF NOT EXISTS work_breakdown_structure (
    tenant_id UUID NOT NULL,
    structure_id UUID NOT NULL,
    code VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, structure_id),
    CONSTRAINT uq_wbs_structure_code_per_tenant UNIQUE (tenant_id, code)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_wbs_structure_code_ci
    ON work_breakdown_structure (tenant_id, lower(code));

CREATE TABLE IF NOT EXISTS work_breakdown_node (
    tenant_id UUID NOT NULL,
    structure_id UUID NOT NULL,
    node_id UUID NOT NULL,
    parent_id UUID,
    code VARCHAR(120) NOT NULL,
    name VARCHAR(500) NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0),
    PRIMARY KEY (tenant_id, structure_id, node_id),
    CONSTRAINT fk_wbs_node_structure
        FOREIGN KEY (tenant_id, structure_id)
        REFERENCES work_breakdown_structure (tenant_id, structure_id),
    CONSTRAINT fk_wbs_node_parent
        FOREIGN KEY (tenant_id, structure_id, parent_id)
        REFERENCES work_breakdown_node (tenant_id, structure_id, node_id)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_wbs_node_not_self_parent CHECK (parent_id IS NULL OR parent_id <> node_id),
    CONSTRAINT uq_wbs_node_sibling_code UNIQUE NULLS NOT DISTINCT (tenant_id, structure_id, parent_id, code),
    CONSTRAINT uq_wbs_node_sibling_position UNIQUE NULLS NOT DISTINCT (tenant_id, structure_id, parent_id, position)
);

CREATE OR REPLACE FUNCTION reject_work_breakdown_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Work breakdown structures are immutable snapshots; UPDATE/DELETE is forbidden';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_wbs_structure_immutable ON work_breakdown_structure;
CREATE TRIGGER trg_wbs_structure_immutable
    BEFORE UPDATE OR DELETE ON work_breakdown_structure
    FOR EACH ROW EXECUTE FUNCTION reject_work_breakdown_mutation();

DROP TRIGGER IF EXISTS trg_wbs_node_immutable ON work_breakdown_node;
CREATE TRIGGER trg_wbs_node_immutable
    BEFORE UPDATE OR DELETE ON work_breakdown_node
    FOR EACH ROW EXECUTE FUNCTION reject_work_breakdown_mutation();
