ALTER TABLE courier_parcels
    ADD CONSTRAINT uq_courier_parcels_tenant_shipment_id UNIQUE (tenant_id, shipment_id, id);

CREATE TABLE courier_tracking_events (
    tenant_id UUID NOT NULL,
    id UUID NOT NULL,
    shipment_id UUID NOT NULL,
    parcel_id UUID,
    event_type VARCHAR(48) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    location_code VARCHAR(64),
    actor_reference VARCHAR(160),
    source VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    note VARCHAR(1000),
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_courier_tracking_shipment
        FOREIGN KEY (tenant_id, shipment_id)
        REFERENCES courier_shipments (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_courier_tracking_parcel
        FOREIGN KEY (tenant_id, shipment_id, parcel_id)
        REFERENCES courier_parcels (tenant_id, shipment_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_courier_tracking_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_courier_tracking_recorded_time CHECK (recorded_at >= occurred_at),
    CONSTRAINT ck_courier_tracking_event_type CHECK (event_type IN (
        'SHIPMENT_BOOKED', 'PICKED_UP', 'ARRIVED_AT_HUB', 'DEPARTED_HUB', 'SORTED_AT_HUB',
        'LOADED_FOR_LINEHAUL', 'LINEHAUL_DEPARTED', 'LINEHAUL_ARRIVED',
        'ARRIVED_AT_DESTINATION_DEPOT', 'OUT_FOR_DELIVERY', 'DELIVERY_ATTEMPTED',
        'DELIVERY_FAILED', 'DELIVERED', 'HELD', 'ADDRESS_CORRECTED', 'RETURN_INITIATED',
        'RETURN_IN_TRANSIT', 'RETURNED_TO_SENDER', 'EXCEPTION_RECORDED'
    ))
);

CREATE INDEX ix_courier_tracking_tenant_shipment_timeline
    ON courier_tracking_events (tenant_id, shipment_id, occurred_at DESC, id DESC);
CREATE INDEX ix_courier_tracking_tenant_parcel_timeline
    ON courier_tracking_events (tenant_id, parcel_id, occurred_at DESC)
    WHERE parcel_id IS NOT NULL;

-- This table is intentionally append-only. Runtime DB roles should receive INSERT/SELECT,
-- not UPDATE/DELETE, on courier_tracking_events.
