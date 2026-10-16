-- ASSET-24 (asset-side document references) + ASSET-25 (accounting link, snapshots, reconciliation)

create table if not exists asset_document_reference (
    id uuid primary key,
    tenant_id varchar(100) not null,
    asset_id uuid not null,
    document_id uuid not null,
    document_version integer,
    document_type varchar(100) not null,
    title varchar(255) not null,
    primary_document boolean not null default false,
    required boolean not null default false,
    valid_from timestamp with time zone,
    valid_until timestamp with time zone,
    linked_at timestamp with time zone not null,
    linked_by varchar(100),
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone,
    version bigint
);

create index if not exists ix_asset_document_tenant_asset
    on asset_document_reference (tenant_id, asset_id);

create index if not exists ix_asset_document_tenant_expiry
    on asset_document_reference (tenant_id, valid_until);

-- at most one primary document per (tenant, asset, type)
create unique index if not exists ux_asset_document_primary
    on asset_document_reference (tenant_id, asset_id, document_type)
    where primary_document = true;

create table if not exists asset_accounting_link (
    id uuid primary key,
    tenant_id varchar(100) not null,
    asset_id uuid not null,
    accounting_asset_id uuid not null,
    accounting_asset_number varchar(100),
    linked_at timestamp with time zone not null,
    linked_by varchar(100),
    link_active boolean not null default true,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone,
    version bigint
);

create unique index if not exists ux_asset_accounting_link
    on asset_accounting_link (tenant_id, asset_id)
    where link_active = true;

create index if not exists ix_asset_accounting_link_accounting
    on asset_accounting_link (tenant_id, accounting_asset_id);

create table if not exists asset_financial_snapshot (
    id uuid primary key,
    tenant_id varchar(100) not null,
    asset_id uuid not null,
    accounting_asset_id uuid,
    acquisition_cost numeric(19,4),
    accumulated_depreciation numeric(19,4),
    net_book_value numeric(19,4),
    impairment_amount numeric(19,4),
    proceeds numeric(19,4),
    currency varchar(3),
    capitalization_date timestamp with time zone,
    last_depreciation_date timestamp with time zone,
    disposed_at timestamp with time zone,
    fixed_asset_status varchar(50),
    as_of timestamp with time zone,
    source_event_key varchar(200),
    source_kind varchar(50),
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone,
    version bigint
);

create unique index if not exists ux_financial_snapshot_asset
    on asset_financial_snapshot (tenant_id, asset_id);

create index if not exists ix_financial_snapshot_source
    on asset_financial_snapshot (tenant_id, source_event_key);

create table if not exists asset_accounting_reconciliation (
    id uuid primary key,
    tenant_id varchar(100) not null,
    asset_id uuid not null,
    accounting_asset_id uuid,
    reconciliation_status varchar(50) not null,
    detail varchar(1000),
    reconciled_at timestamp with time zone not null,
    reconciled_by varchar(100),
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone,
    version bigint
);

create index if not exists ix_reconciliation_tenant_asset
    on asset_accounting_reconciliation (tenant_id, asset_id, reconciled_at);