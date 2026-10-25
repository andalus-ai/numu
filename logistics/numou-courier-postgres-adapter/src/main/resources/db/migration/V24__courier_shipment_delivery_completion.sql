-- M24: make shipment-level delivery completion an explicit, guarded lifecycle state.
ALTER TABLE courier_shipments
    ADD COLUMN delivered_at TIMESTAMPTZ;

ALTER TABLE courier_shipments
    DROP CONSTRAINT ck_courier_shipments_status;

ALTER TABLE courier_shipments
    ADD CONSTRAINT ck_courier_shipments_status
    CHECK (status IN ('DRAFT', 'BOOKED', 'CANCELLED', 'DELIVERED'));

ALTER TABLE courier_shipments
    ADD CONSTRAINT ck_courier_shipments_delivery_time
    CHECK (
        (status = 'DELIVERED') = (delivered_at IS NOT NULL)
        AND (delivered_at IS NULL OR
             (booked_at IS NOT NULL AND delivered_at >= booked_at AND delivered_at <= updated_at))
        AND (status <> 'DELIVERED' OR booked_at IS NOT NULL)
    );

CREATE INDEX ix_courier_shipments_tenant_delivered
    ON courier_shipments (tenant_id, delivered_at DESC)
    WHERE status = 'DELIVERED';
