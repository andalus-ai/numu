CREATE TABLE courier_dispatch_waves (
    tenant_id UUID NOT NULL,
    id UUID NOT NULL,
    reference VARCHAR(80) NOT NULL,
    origin_hub_reference VARCHAR(160) NOT NULL,
    destination_hub_reference VARCHAR(160) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PLANNED',
    authorized_exception_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    departed_at TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT uq_courier_dispatch_wave_reference UNIQUE (tenant_id, reference),
    CONSTRAINT ck_courier_dispatch_wave_status CHECK (status IN ('PLANNED','LOADING','READY','DEPARTED','CANCELLED')),
    CONSTRAINT ck_courier_dispatch_wave_hubs CHECK (origin_hub_reference <> destination_hub_reference),
    CONSTRAINT ck_courier_dispatch_wave_timestamps CHECK (
        updated_at >= created_at
        AND (departed_at IS NULL OR (departed_at >= created_at AND departed_at <= updated_at))
        AND (status <> 'DEPARTED' OR departed_at IS NOT NULL)
    )
);

CREATE TABLE courier_dispatch_wave_parcels (
    tenant_id UUID NOT NULL,
    wave_id UUID NOT NULL,
    parcel_reference VARCHAR(160) NOT NULL,
    expected BOOLEAN NOT NULL DEFAULT TRUE,
    first_scanned_at TIMESTAMPTZ,
    scan_count INTEGER NOT NULL DEFAULT 0,
    discrepancy VARCHAR(24),
    PRIMARY KEY (tenant_id, wave_id, parcel_reference),
    CONSTRAINT fk_courier_dispatch_wave_parcels_wave FOREIGN KEY (tenant_id, wave_id)
        REFERENCES courier_dispatch_waves (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_courier_dispatch_wave_scan_count CHECK (scan_count >= 0),
    CONSTRAINT ck_courier_dispatch_wave_discrepancy CHECK (discrepancy IS NULL OR discrepancy IN ('UNEXPECTED','DUPLICATE')),
    CONSTRAINT ck_courier_dispatch_wave_scan_time CHECK ((scan_count = 0 AND first_scanned_at IS NULL) OR (scan_count > 0 AND first_scanned_at IS NOT NULL))
);

CREATE INDEX ix_courier_dispatch_wave_status
    ON courier_dispatch_waves (tenant_id, status, origin_hub_reference, destination_hub_reference, created_at);

CREATE INDEX ix_courier_dispatch_wave_parcel_discrepancies
    ON courier_dispatch_wave_parcels (tenant_id, wave_id, discrepancy)
    WHERE discrepancy IS NOT NULL;
