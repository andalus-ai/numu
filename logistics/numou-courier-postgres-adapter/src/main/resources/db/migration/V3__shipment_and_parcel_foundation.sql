CREATE TABLE courier_shipments (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    reference VARCHAR(40) NOT NULL,
    normalized_reference VARCHAR(40) NOT NULL,
    status VARCHAR(16) NOT NULL,
    service_level VARCHAR(16) NOT NULL,
    sender_contact_name VARCHAR(160) NOT NULL,
    sender_phone VARCHAR(48) NOT NULL,
    sender_address_line_1 VARCHAR(240) NOT NULL,
    sender_address_line_2 VARCHAR(240),
    sender_locality VARCHAR(120) NOT NULL,
    sender_administrative_area VARCHAR(120),
    sender_postal_code VARCHAR(24),
    sender_country_code CHAR(2) NOT NULL,
    recipient_contact_name VARCHAR(160) NOT NULL,
    recipient_phone VARCHAR(48) NOT NULL,
    recipient_address_line_1 VARCHAR(240) NOT NULL,
    recipient_address_line_2 VARCHAR(240),
    recipient_locality VARCHAR(120) NOT NULL,
    recipient_administrative_area VARCHAR(120),
    recipient_postal_code VARCHAR(24),
    recipient_country_code CHAR(2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    booked_at TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT uq_courier_shipments_tenant_reference UNIQUE (tenant_id, normalized_reference),
    CONSTRAINT ck_courier_shipments_status CHECK (status IN ('DRAFT', 'BOOKED', 'CANCELLED')),
    CONSTRAINT ck_courier_shipments_service CHECK (service_level IN ('ECONOMY', 'STANDARD', 'EXPRESS', 'SAME_DAY')),
    CONSTRAINT ck_courier_shipments_reference CHECK (normalized_reference = upper(trim(reference))),
    CONSTRAINT ck_courier_shipments_dates CHECK (updated_at >= created_at AND (booked_at IS NULL OR (booked_at >= created_at AND booked_at <= updated_at)) AND (status <> 'BOOKED' OR booked_at IS NOT NULL) AND (status <> 'DRAFT' OR booked_at IS NULL)),
    CONSTRAINT ck_courier_shipments_country_codes CHECK (
        sender_country_code ~ '^[A-Z]{2}$' AND recipient_country_code ~ '^[A-Z]{2}$'
    )
);

CREATE INDEX ix_courier_shipments_tenant_status_created
    ON courier_shipments (tenant_id, status, created_at DESC);

CREATE TABLE courier_parcels (
    tenant_id UUID NOT NULL,
    id UUID NOT NULL,
    shipment_id UUID NOT NULL,
    reference VARCHAR(40) NOT NULL,
    normalized_reference VARCHAR(40) NOT NULL,
    weight_kg NUMERIC(12,3) NOT NULL,
    length_cm NUMERIC(12,2) NOT NULL,
    width_cm NUMERIC(12,2) NOT NULL,
    height_cm NUMERIC(12,2) NOT NULL,
    handling_instructions VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_courier_parcels_shipment FOREIGN KEY (tenant_id, shipment_id)
        REFERENCES courier_shipments (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_courier_parcels_shipment_reference UNIQUE (tenant_id, shipment_id, normalized_reference),
    CONSTRAINT ck_courier_parcels_reference CHECK (normalized_reference = upper(trim(reference))),
    CONSTRAINT ck_courier_parcels_positive_dimensions CHECK (
        weight_kg > 0 AND length_cm > 0 AND width_cm > 0 AND height_cm > 0
    )
);

CREATE INDEX ix_courier_parcels_tenant_shipment
    ON courier_parcels (tenant_id, shipment_id);
