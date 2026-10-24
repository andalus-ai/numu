CREATE TABLE courier_delivery_tasks (
    tenant_id UUID NOT NULL,
    id UUID NOT NULL,
    shipment_id UUID NOT NULL,
    courier_reference VARCHAR(160) NOT NULL,
    assigned_by VARCHAR(160) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    out_for_delivery_at TIMESTAMPTZ,
    last_attempt_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    recipient_name VARCHAR(160),
    proof_reference VARCHAR(512),
    failure_reason VARCHAR(500),
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_courier_delivery_task_shipment FOREIGN KEY (tenant_id, shipment_id)
        REFERENCES courier_shipments (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_courier_delivery_task_id_shipment UNIQUE (tenant_id, id, shipment_id),
    CONSTRAINT ck_courier_delivery_task_status CHECK (status IN
        ('ASSIGNED', 'OUT_FOR_DELIVERY', 'DELIVERY_ATTEMPTED', 'DELIVERED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_courier_delivery_task_dates CHECK (
        updated_at >= created_at
        AND (out_for_delivery_at IS NULL OR (out_for_delivery_at >= created_at AND out_for_delivery_at <= updated_at))
        AND (last_attempt_at IS NULL OR (out_for_delivery_at IS NOT NULL AND last_attempt_at >= out_for_delivery_at AND last_attempt_at <= updated_at))
        AND (delivered_at IS NULL OR (out_for_delivery_at IS NOT NULL AND delivered_at >= out_for_delivery_at AND delivered_at <= updated_at))
        AND (status <> 'OUT_FOR_DELIVERY' OR out_for_delivery_at IS NOT NULL)
        AND (status <> 'DELIVERY_ATTEMPTED' OR (last_attempt_at IS NOT NULL AND failure_reason IS NOT NULL))
        AND (status <> 'DELIVERED' OR (delivered_at IS NOT NULL AND recipient_name IS NOT NULL AND proof_reference IS NOT NULL))
        AND (status NOT IN ('FAILED', 'DELIVERY_ATTEMPTED') OR failure_reason IS NOT NULL)
    )
);

-- Only one non-terminal delivery task per shipment. A retry creates a new task only after
-- the previous task is FAILED or CANCELLED; delivered shipments cannot be reassigned.
CREATE UNIQUE INDEX uq_courier_delivery_active_shipment
    ON courier_delivery_tasks (tenant_id, shipment_id)
    WHERE status IN ('ASSIGNED', 'OUT_FOR_DELIVERY', 'DELIVERY_ATTEMPTED');

CREATE INDEX ix_courier_delivery_courier_queue
    ON courier_delivery_tasks (tenant_id, courier_reference, status, created_at DESC);
CREATE INDEX ix_courier_delivery_shipment_history
    ON courier_delivery_tasks (tenant_id, shipment_id, created_at DESC);
