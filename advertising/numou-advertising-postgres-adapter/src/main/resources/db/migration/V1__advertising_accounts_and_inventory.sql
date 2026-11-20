CREATE TABLE advertising_advertiser_account (
    tenant_id VARCHAR(200) NOT NULL,
    account_id UUID NOT NULL,
    party_reference VARCHAR(255) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    account_status VARCHAR(24) NOT NULL CHECK (account_status IN ('PENDING','ACTIVE','SUSPENDED','CLOSED')),
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    PRIMARY KEY (tenant_id, account_id),
    CONSTRAINT uq_advertising_advertiser_party UNIQUE (tenant_id, party_reference)
);

CREATE INDEX ix_advertising_advertiser_tenant_status
    ON advertising_advertiser_account (tenant_id, account_status, created_at);

CREATE TABLE advertising_publisher_account (
    tenant_id VARCHAR(200) NOT NULL,
    account_id UUID NOT NULL,
    party_reference VARCHAR(255) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    account_status VARCHAR(24) NOT NULL CHECK (account_status IN ('PENDING','ACTIVE','SUSPENDED','CLOSED')),
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    PRIMARY KEY (tenant_id, account_id),
    CONSTRAINT uq_advertising_publisher_party UNIQUE (tenant_id, party_reference)
);

CREATE INDEX ix_advertising_publisher_tenant_status
    ON advertising_publisher_account (tenant_id, account_status, created_at);

CREATE TABLE advertising_publisher_property (
    tenant_id VARCHAR(200) NOT NULL,
    property_id UUID NOT NULL,
    publisher_account_id UUID NOT NULL,
    property_name VARCHAR(200) NOT NULL,
    canonical_uri VARCHAR(2048) NOT NULL,
    property_status VARCHAR(32) NOT NULL CHECK (property_status IN ('PENDING_VERIFICATION','VERIFIED','SUSPENDED','RETIRED')),
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    PRIMARY KEY (tenant_id, property_id),
    CONSTRAINT fk_advertising_property_publisher
        FOREIGN KEY (tenant_id, publisher_account_id)
        REFERENCES advertising_publisher_account (tenant_id, account_id) ON DELETE RESTRICT,
    CONSTRAINT uq_advertising_property_uri UNIQUE (tenant_id, canonical_uri)
);

CREATE INDEX ix_advertising_property_tenant_publisher
    ON advertising_publisher_property (tenant_id, publisher_account_id, property_status);

CREATE TABLE advertising_ad_placement (
    tenant_id VARCHAR(200) NOT NULL,
    placement_id UUID NOT NULL,
    property_id UUID NOT NULL,
    placement_code VARCHAR(100) NOT NULL,
    format VARCHAR(100) NOT NULL,
    placement_status VARCHAR(24) NOT NULL CHECK (placement_status IN ('DRAFT','ENABLED','DISABLED','RETIRED')),
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    PRIMARY KEY (tenant_id, placement_id),
    CONSTRAINT fk_advertising_placement_property
        FOREIGN KEY (tenant_id, property_id)
        REFERENCES advertising_publisher_property (tenant_id, property_id) ON DELETE RESTRICT,
    CONSTRAINT uq_advertising_placement_code UNIQUE (tenant_id, property_id, placement_code)
);

CREATE INDEX ix_advertising_placement_tenant_property_status
    ON advertising_ad_placement (tenant_id, property_id, placement_status);
