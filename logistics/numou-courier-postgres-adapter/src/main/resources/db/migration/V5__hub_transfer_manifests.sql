CREATE TABLE courier_hub_transfers (
    tenant_id UUID NOT NULL,
    id UUID NOT NULL,
    reference VARCHAR(40) NOT NULL,
    normalized_reference VARCHAR(40) NOT NULL,
    origin_hub_id UUID NOT NULL,
    destination_hub_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    departed_at TIMESTAMPTZ,
    arrived_at TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT uq_courier_hub_transfers_tenant_reference UNIQUE (tenant_id, normalized_reference),
    CONSTRAINT fk_courier_transfer_origin FOREIGN KEY (tenant_id, origin_hub_id)
        REFERENCES courier_hubs (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_courier_transfer_destination FOREIGN KEY (tenant_id, destination_hub_id)
        REFERENCES courier_hubs (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_courier_transfer_distinct_hubs CHECK (origin_hub_id <> destination_hub_id),
    CONSTRAINT ck_courier_transfer_reference CHECK (normalized_reference = upper(trim(reference))),
    CONSTRAINT ck_courier_transfer_status CHECK (status IN ('DRAFT', 'SEALED', 'DEPARTED', 'ARRIVED', 'CANCELLED')),
    CONSTRAINT ck_courier_transfer_times CHECK (
        updated_at >= created_at
        AND (departed_at IS NULL OR (departed_at >= created_at AND departed_at <= updated_at))
        AND (arrived_at IS NULL OR (departed_at IS NOT NULL AND arrived_at >= departed_at AND arrived_at <= updated_at))
        AND (status NOT IN ('DEPARTED', 'ARRIVED') OR departed_at IS NOT NULL)
        AND (status <> 'ARRIVED' OR arrived_at IS NOT NULL)
    )
);

CREATE TABLE courier_hub_transfer_shipments (
    tenant_id UUID NOT NULL,
    transfer_id UUID NOT NULL,
    shipment_id UUID NOT NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, transfer_id, shipment_id),
    CONSTRAINT fk_courier_transfer_shipments_transfer FOREIGN KEY (tenant_id, transfer_id)
        REFERENCES courier_hub_transfers (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_courier_transfer_shipments_shipment FOREIGN KEY (tenant_id, shipment_id)
        REFERENCES courier_shipments (tenant_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_courier_hub_transfers_tenant_status_created
    ON courier_hub_transfers (tenant_id, status, created_at DESC);
CREATE INDEX ix_courier_transfer_shipments_tenant_shipment
    ON courier_hub_transfer_shipments (tenant_id, shipment_id);
