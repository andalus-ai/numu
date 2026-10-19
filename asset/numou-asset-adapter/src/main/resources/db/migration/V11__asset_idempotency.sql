-- ASSET-28 integration hardening: idempotency keys for external writes
create table if not exists asset_idempotency_key (
    id uuid primary key,
    tenant_id varchar(100) not null,
    idempotency_key varchar(200) not null,
    operation varchar(100) not null,
    request_fingerprint varchar(512),
    stored_at timestamptz not null,
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz,
    version bigint
);
create unique index if not exists ux_asset_idempotency_tenant_key
    on asset_idempotency_key (tenant_id, idempotency_key);