-- M16: retain load discrepancies as durable operational evidence instead of
-- rolling back the observation or recording it as a successful load.
ALTER TABLE courier_hub_scan_receipts
    ADD COLUMN discrepancy_reason VARCHAR(500);

ALTER TABLE courier_hub_scan_receipts
    DROP CONSTRAINT ck_courier_scan_type;

ALTER TABLE courier_hub_scan_receipts
    ADD CONSTRAINT ck_courier_scan_type CHECK (scan_type IN (
        'PICKUP_COLLECTED', 'HUB_INTAKE', 'SORTED', 'DISPATCH_LOADED', 'LOAD_DISCREPANCY',
        'CONTAINER_SEALED', 'CONTAINER_DISPATCHED', 'HUB_RECEIVED',
        'CONTAINER_RECEIVED', 'EXCEPTION_RECORDED'
    ));

ALTER TABLE courier_hub_scan_receipts
    DROP CONSTRAINT ck_courier_scan_custody_fields;

ALTER TABLE courier_hub_scan_receipts
    ADD CONSTRAINT ck_courier_scan_custody_fields CHECK (
        (scan_type NOT IN ('DISPATCH_LOADED','LOAD_DISCREPANCY') OR dispatch_wave_id IS NOT NULL)
        AND (scan_type <> 'CONTAINER_SEALED' OR (container_id IS NOT NULL AND seal_reference IS NOT NULL))
        AND (scan_type <> 'CONTAINER_DISPATCHED' OR
            (container_id IS NOT NULL AND dispatch_wave_id IS NOT NULL AND manifest_reference IS NOT NULL))
        AND (scan_type <> 'CONTAINER_RECEIVED' OR
            (container_id IS NOT NULL AND observed_seal_reference IS NOT NULL))
        AND (scan_type <> 'LOAD_DISCREPANCY' OR discrepancy_reason IS NOT NULL)
    ) NOT VALID;

ALTER TABLE courier_tracking_events
    DROP CONSTRAINT ck_courier_tracking_event_type;

ALTER TABLE courier_tracking_events
    ADD CONSTRAINT ck_courier_tracking_event_type CHECK (event_type IN (
        'SHIPMENT_BOOKED', 'PICKED_UP', 'ARRIVED_AT_HUB', 'DEPARTED_HUB', 'SORTED_AT_HUB',
        'LOADED_FOR_LINEHAUL', 'LOAD_DISCREPANCY', 'LINEHAUL_DEPARTED', 'LINEHAUL_ARRIVED',
        'ARRIVED_AT_DESTINATION_DEPOT', 'OUT_FOR_DELIVERY', 'DELIVERY_ATTEMPTED',
        'DELIVERY_FAILED', 'DELIVERED', 'HELD', 'ADDRESS_CORRECTED', 'RETURN_INITIATED',
        'RETURN_IN_TRANSIT', 'RETURNED_TO_SENDER', 'CONTAINER_SEALED', 'EXCEPTION_RECORDED'
    ));

CREATE TABLE courier_dispatch_wave_discrepancies (
    tenant_id UUID NOT NULL,
    id UUID NOT NULL,
    wave_id UUID NOT NULL,
    parcel_reference VARCHAR(160) NOT NULL,
    discrepancy_type VARCHAR(24) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    source VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    actor_reference VARCHAR(160),
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_courier_wave_discrepancy_wave FOREIGN KEY (tenant_id, wave_id)
        REFERENCES courier_dispatch_waves (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_courier_wave_discrepancy_idempotency UNIQUE (tenant_id, source, idempotency_key),
    CONSTRAINT ck_courier_wave_discrepancy_type CHECK (discrepancy_type IN ('UNEXPECTED_PARCEL','DUPLICATE_SCAN','MISSING_PARCEL')),
    CONSTRAINT ck_courier_wave_discrepancy_reason CHECK (length(trim(reason)) BETWEEN 1 AND 500),
    CONSTRAINT ck_courier_wave_discrepancy_time CHECK (recorded_at >= occurred_at)
);

CREATE INDEX ix_courier_wave_discrepancy_timeline
    ON courier_dispatch_wave_discrepancies (tenant_id, wave_id, occurred_at DESC);
