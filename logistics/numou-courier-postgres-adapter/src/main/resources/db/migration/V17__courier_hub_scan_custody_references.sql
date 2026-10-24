ALTER TABLE courier_hub_scan_receipts
    ADD COLUMN container_id UUID,
    ADD COLUMN dispatch_wave_id UUID,
    ADD COLUMN seal_reference VARCHAR(160),
    ADD COLUMN manifest_reference VARCHAR(160),
    ADD COLUMN observed_seal_reference VARCHAR(160);

ALTER TABLE courier_hub_scan_receipts
    ADD CONSTRAINT fk_courier_scan_container
        FOREIGN KEY (tenant_id, container_id)
        REFERENCES courier_transport_containers (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_courier_scan_dispatch_wave
        FOREIGN KEY (tenant_id, dispatch_wave_id)
        REFERENCES courier_dispatch_waves (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_courier_scan_custody_fields CHECK (
        (scan_type <> 'DISPATCH_LOADED' OR dispatch_wave_id IS NOT NULL)
        AND (scan_type <> 'CONTAINER_SEALED' OR (container_id IS NOT NULL AND seal_reference IS NOT NULL))
        AND (scan_type <> 'CONTAINER_DISPATCHED' OR
            (container_id IS NOT NULL AND dispatch_wave_id IS NOT NULL AND manifest_reference IS NOT NULL))
        AND (scan_type <> 'CONTAINER_RECEIVED' OR
            (container_id IS NOT NULL AND observed_seal_reference IS NOT NULL))
    ) NOT VALID;

CREATE INDEX ix_courier_scan_container_timeline
    ON courier_hub_scan_receipts (tenant_id, container_id, occurred_at DESC)
    WHERE container_id IS NOT NULL;

CREATE INDEX ix_courier_scan_dispatch_wave_timeline
    ON courier_hub_scan_receipts (tenant_id, dispatch_wave_id, occurred_at DESC)
    WHERE dispatch_wave_id IS NOT NULL;
