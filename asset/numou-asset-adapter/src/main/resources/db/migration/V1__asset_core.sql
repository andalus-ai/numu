-- Asset bounded context — initial schema (ASSET-09 / ASSET-11 / ASSET-13 / ASSET-15 / ASSET-16)

create table if not exists asset (
    id uuid primary key,
    tenant_id varchar(100) not null,
    asset_number varchar(100) not null,
    name varchar(255) not null,
    asset_type varchar(50) not null,
    status varchar(50) not null,
    location_id varchar(100),
    location_name varchar(255),
    party_id varchar(100),
    party_type varchar(50),
    party_name varchar(255),
    classification_id varchar(100),
    classification_name varchar(255),
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone,
    version bigint
);

create unique index if not exists uq_asset_tenant_number
    on asset (tenant_id, asset_number);

create index if not exists idx_asset_tenant_status
    on asset (tenant_id, status);

create index if not exists idx_asset_tenant_location
    on asset (tenant_id, location_id);

create index if not exists idx_asset_tenant_party
    on asset (tenant_id, party_id);

-- Transactional outbox (ASSET-11)
create table if not exists asset_outbox (
    id uuid primary key,
    tenant_id varchar(100),
    aggregate_type varchar(100) not null,
    event_type varchar(150) not null,
    payload varchar(4000),
    occurred_at timestamp with time zone not null,
    processed boolean not null default false,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone,
    version bigint
);

create index if not exists idx_asset_outbox_unprocessed
    on asset_outbox (processed, created_at);

-- Asset-to-asset relationships (ASSET-16)
create table if not exists asset_relationship (
    id uuid primary key,
    tenant_id varchar(100) not null,
    source_asset_id uuid not null,
    related_asset_id uuid not null,
    relationship_type varchar(50) not null,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone,
    version bigint
);

create unique index if not exists uq_asset_relationship
    on asset_relationship (tenant_id, source_asset_id, related_asset_id, relationship_type);

create index if not exists idx_asset_relationship_source
    on asset_relationship (tenant_id, source_asset_id);

-- Append-only movement history (ASSET-13)
create table if not exists asset_movement (
    id uuid primary key,
    tenant_id varchar(100) not null,
    asset_id uuid not null,
    movement_type varchar(40) not null,
    occurred_at timestamp with time zone not null,
    from_location_id varchar(100),
    from_party_id varchar(100),
    to_location_id varchar(100),
    to_party_id varchar(100),
    classification_id varchar(100),
    related_asset_id uuid,
    source_event_id uuid not null unique,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone,
    version bigint
);

create index if not exists idx_asset_movement_asset
    on asset_movement (tenant_id, asset_id, occurred_at);

-- Dynamic attributes (ASSET-15)
create table if not exists asset_attribute (
    id uuid primary key,
    tenant_id varchar(100) not null,
    asset_id uuid not null,
    attribute_key varchar(100) not null,
    attribute_value varchar(1000) not null,
    attribute_type varchar(20) not null,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone,
    version bigint
);

create index if not exists idx_asset_attribute_asset
    on asset_attribute (tenant_id, asset_id);
