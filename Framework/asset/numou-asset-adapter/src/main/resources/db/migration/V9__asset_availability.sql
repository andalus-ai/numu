-- ASSET-26 availability & utilization
create table if not exists asset_availability_period (
    id uuid primary key,
    tenant_id varchar(100) not null,
    asset_id uuid not null,
    starts_at timestamptz not null,
    ends_at timestamptz,
    availability_type varchar(50) not null,
    reason varchar(50) not null,
    reference_id varchar(200),
    notes varchar(1000),
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz,
    version bigint
);
create index if not exists ix_asset_availability_tenant_asset
    on asset_availability_period (tenant_id, asset_id, starts_at);
create index if not exists ix_asset_availability_tenant_asset_open
    on asset_availability_period (tenant_id, asset_id) where ends_at is null;

create table if not exists asset_utilization (
    id uuid primary key,
    tenant_id varchar(100) not null,
    asset_id uuid not null,
    starts_at timestamptz not null,
    ends_at timestamptz not null,
    utilization_type varchar(50) not null,
    quantity numeric(24, 8) not null,
    unit varchar(50),
    source varchar(100),
    reference_id varchar(200),
    occurred_at timestamptz not null,
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz,
    version bigint
);
create index if not exists ix_asset_utilization_tenant_asset_time
    on asset_utilization (tenant_id, asset_id, starts_at);
create unique index if not exists ux_asset_utilization_source_ref
    on asset_utilization (tenant_id, source, reference_id) where reference_id is not null;