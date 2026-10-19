-- ASSET-21 meter definitions + readings
create table if not exists asset_meter (
    id uuid primary key,
    tenant_id varchar(100) not null,
    asset_id uuid not null,
    meter_type varchar(50) not null,
    unit varchar(50) not null,
    behavior varchar(50) not null,
    name varchar(255) not null,
    replacement_of uuid,
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz,
    version bigint
);
create index if not exists ix_asset_meter_tenant_asset on asset_meter (tenant_id, asset_id);
create unique index if not exists ux_asset_meter_tenant_asset_type on asset_meter (tenant_id, asset_id, meter_type);
create table if not exists asset_meter_reading (
    id uuid primary key,
    tenant_id varchar(100) not null,
    meter_id uuid not null,
    asset_id uuid not null,
    reading_value numeric(24, 8) not null,
    unit varchar(50) not null,
    recorded_at timestamptz not null,
    occurred_at timestamptz not null,
    recorded_by varchar(100),
    reading_type varchar(50) not null,
    source varchar(100),
    source_ref varchar(200),
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz,
    version bigint
);
create index if not exists ix_meter_reading_tenant_meter_time on asset_meter_reading (tenant_id, meter_id, recorded_at);
create index if not exists ix_meter_reading_tenant_asset_time on asset_meter_reading (tenant_id, asset_id, recorded_at);
create unique index if not exists ux_meter_reading_source_ref on asset_meter_reading (tenant_id, meter_id, source_ref) where source_ref is not null;
