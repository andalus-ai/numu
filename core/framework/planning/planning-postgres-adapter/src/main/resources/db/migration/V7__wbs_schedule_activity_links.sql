CREATE TABLE IF NOT EXISTS work_breakdown_activity_link (
    tenant_id UUID NOT NULL,
    structure_id UUID NOT NULL,
    node_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    activity_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, structure_id, plan_id, activity_id),
    CONSTRAINT fk_wbs_link_node
        FOREIGN KEY (tenant_id, structure_id, node_id)
        REFERENCES work_breakdown_node (tenant_id, structure_id, node_id),
    CONSTRAINT fk_wbs_link_activity
        FOREIGN KEY (plan_id, activity_id)
        REFERENCES schedule_activity (plan_id, activity_id)
        ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_wbs_link_structure_node
    ON work_breakdown_activity_link (tenant_id, structure_id, node_id);

CREATE INDEX IF NOT EXISTS ix_wbs_link_plan
    ON work_breakdown_activity_link (tenant_id, plan_id);
