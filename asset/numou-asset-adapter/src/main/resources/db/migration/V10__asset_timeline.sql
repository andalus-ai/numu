-- ASSET-27 asset timeline / history read model
create table if not exists asset_timeline (
    id uuid primary key,
    tenant_id varchar(100) not null,
    asset_id uuid not null,
    occurred_at timestamptz not null,
    event_type varchar(150) not null,
    category varchar(50) not null,
    title varchar(255) not null,
    description varchar(2000),
    source varchar(100),
    source_reference varchar(200),
    source_event_id uuid not null,
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz,
    version bigint
);
create index if not exists ix_asset_timeline_tenant_asset_time
    on asset_timeline (tenant_id, asset_id, occurred_at);
-- idempotent projection: one timeline entry per source event
create unique index if not exists ux_asset_timeline_source_event
    on asset_timeline (tenant_id, source_event_id);