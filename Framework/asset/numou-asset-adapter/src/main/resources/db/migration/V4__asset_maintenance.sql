-- ASSET-19 — Maintenance & Work Orders

create table if not exists maintenance_work_order (
    id uuid primary key,
    tenant_id varchar(100) not null,
    asset_id uuid not null,
    work_order_number varchar(100) not null,
    title varchar(255) not null,
    description varchar(2000),
    work_order_type varchar(40) not null,
    priority varchar(20) not null,
    status varchar(30) not null,
    requested_by varchar(100),
    assigned_to varchar(100),
    opened_at timestamp with time zone,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone,
    version bigint
);

create unique index if not exists uq_maintenance_wo_tenant_number
    on maintenance_work_order (tenant_id, work_order_number);

create index if not exists idx_maintenance_wo_tenant_asset
    on maintenance_work_order (tenant_id, asset_id);

create index if not exists idx_maintenance_wo_tenant_status
    on maintenance_work_order (tenant_id, status);

create table if not exists maintenance_task (
    id uuid primary key,
    tenant_id varchar(100) not null,
    work_order_id uuid not null,
    task_number varchar(100) not null,
    title varchar(255) not null,
    description varchar(2000),
    sequence integer not null default 0,
    status varchar(30) not null,
    completed_at timestamp with time zone,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone,
    version bigint
);

create index if not exists idx_maintenance_task_work_order
    on maintenance_task (tenant_id, work_order_id, sequence);