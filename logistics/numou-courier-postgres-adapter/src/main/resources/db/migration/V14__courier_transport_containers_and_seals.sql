CREATE TABLE courier_transport_containers (
    tenant_id UUID NOT NULL,
    id UUID NOT NULL,
    reference VARCHAR(80) NOT NULL,
    container_type VARCHAR(24) NOT NULL,
    origin_hub_reference VARCHAR(160) NOT NULL,
    destination_hub_reference VARCHAR(160) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    seal_reference VARCHAR(160),
    sealed_by VARCHAR(160),
    sealed_at TIMESTAMPTZ,
    dispatched_with_manifest_reference VARCHAR(160),
    dispatched_at TIMESTAMPTZ,
    received_by VARCHAR(160),
    received_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT uq_courier_container_reference UNIQUE (tenant_id, reference),
    CONSTRAINT uq_courier_container_seal UNIQUE (tenant_id, seal_reference),
    CONSTRAINT ck_courier_container_type CHECK (container_type IN ('BAG','CAGE','PALLET','ROLL_CAGE','TOTE')),
    CONSTRAINT ck_courier_container_status CHECK (status IN ('OPEN','SEALED','DISPATCHED','RECEIVED','CANCELLED')),
    CONSTRAINT ck_courier_container_hubs CHECK (origin_hub_reference <> destination_hub_reference),
    CONSTRAINT ck_courier_container_timestamps CHECK (
        updated_at >= created_at
        AND (sealed_at IS NULL OR (sealed_at >= created_at AND sealed_at <= updated_at))
        AND (dispatched_at IS NULL OR (sealed_at IS NOT NULL AND dispatched_at >= sealed_at AND dispatched_at <= updated_at))
        AND (received_at IS NULL OR (dispatched_at IS NOT NULL AND received_at >= dispatched_at AND received_at <= updated_at))
    ),
    CONSTRAINT ck_courier_container_seal_state CHECK (
        status NOT IN ('SEALED','DISPATCHED','RECEIVED')
        OR (seal_reference IS NOT NULL AND sealed_by IS NOT NULL AND sealed_at IS NOT NULL)
    ),
    CONSTRAINT ck_courier_container_dispatch_state CHECK (
        status NOT IN ('DISPATCHED','RECEIVED')
        OR (dispatched_with_manifest_reference IS NOT NULL AND dispatched_at IS NOT NULL)
    ),
    CONSTRAINT ck_courier_container_received_state CHECK (
        status <> 'RECEIVED' OR (received_by IS NOT NULL AND received_at IS NOT NULL)
    )
);

CREATE TABLE courier_transport_container_parcels (
    tenant_id UUID NOT NULL,
    container_id UUID NOT NULL,
    parcel_reference VARCHAR(160) NOT NULL,
    added_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, container_id, parcel_reference),
    CONSTRAINT fk_courier_container_parcel_container FOREIGN KEY (tenant_id, container_id)
        REFERENCES courier_transport_containers (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_courier_parcel_single_container UNIQUE (tenant_id, parcel_reference)
);

CREATE INDEX ix_courier_container_route_status
    ON courier_transport_containers (tenant_id, origin_hub_reference, destination_hub_reference, status, created_at);

CREATE INDEX ix_courier_container_parcel_lookup
    ON courier_transport_container_parcels (tenant_id, parcel_reference);
