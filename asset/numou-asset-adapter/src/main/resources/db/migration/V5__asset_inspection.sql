-- ASSET-20 — Asset Condition & Inspection

create table if not exists asset_inspection (
    id uuid primary key,
    tenant_id varchar(100) not null,
    asset_id uuid not null,
    inspection_number varchar(100) not null,
    inspection_type varchar(40) not null,
    status varchar(30) not null,
    overall_condition varchar(30),
    result varchar(30),
    inspector_id varchar(100),
    notes varchar(2000),
    work_order_id uuid,
    scheduled_for timestamp with time zone,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone,
    version bigint
);

create unique index if not exists uq_inspection_tenant_number
    on asset_inspection (tenant_id, inspection_number);

create index if not exists idx_inspection_tenant_asset
    on asset_inspection (tenant_id, asset_id, created_at);

create index if not exists idx_inspection_tenant_status
    on asset_inspection (tenant_id, status);

create table if not exists asset_inspection_item (
    id uuid primary key,
    tenant_id varchar(100) not null,
    inspection_id uuid not null,
    component varchar(255) not null,
    description varchar(1000),
    condition varchar(30),
    result varchar(30),
    notes varchar(1000),
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone,
    version bigint
);

create index if not exists idx_inspection_item_inspection
    on asset_inspection_item (tenant_id, inspection_id);

create table if not exists asset_inspection_finding (
    id uuid primary key,
    tenant_id varchar(100) not null,
    inspection_id uuid not null,
    asset_id uuid not null,
    category varchar(100),
    description varchar(2000) not null,
    severity varchar(30) not null,
    recommended_action varchar(1000),
    work_order_id uuid,
    recorded_at timestamp with time zone not null,
    recorded_by varchar(100),
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone,
    version bigint
);

create index if not exists idx_inspection_finding_inspection
    on asset_inspection_finding (tenant_id, inspection_id);

create index if not exists idx_inspection_finding_asset
    on asset_inspection_finding (tenant_id, asset_id);