-- PostgreSQL schema for the tenant-owned cargo/courier hub registry.
CREATE TABLE IF NOT EXISTS courier_hubs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    branch_unit_id UUID NOT NULL,
    code VARCHAR(32) NOT NULL,
    normalized_code VARCHAR(32) NOT NULL,
    name VARCHAR(160) NOT NULL,
    hub_type VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    address_line_1 VARCHAR(255) NOT NULL,
    address_line_2 VARCHAR(255),
    locality VARCHAR(120) NOT NULL,
    administrative_area VARCHAR(120),
    postal_code VARCHAR(32),
    country_code CHAR(2) NOT NULL,
    latitude DECIMAL(10,7),
    longitude DECIMAL(10,7),
    time_zone VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_courier_hubs_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_courier_hubs_tenant_code UNIQUE (tenant_id, normalized_code),
    CONSTRAINT ck_courier_hubs_code_normalized CHECK (normalized_code = upper(trim(code))),
    CONSTRAINT ck_courier_hubs_country_code CHECK (country_code = upper(country_code)),
    CONSTRAINT ck_courier_hubs_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_courier_hubs_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_courier_hubs_coordinates_pair CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT ck_courier_hubs_status CHECK (status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'CLOSED'))
);
CREATE INDEX IF NOT EXISTS ix_courier_hubs_tenant_branch
    ON courier_hubs (tenant_id, organization_id, branch_unit_id);
CREATE INDEX IF NOT EXISTS ix_courier_hubs_tenant_status
    ON courier_hubs (tenant_id, status);
