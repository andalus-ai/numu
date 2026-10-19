-- Asset installation history (ASSET-18)
create table if not exists asset_installation (
    id uuid primary key,
    tenant_id varchar(100) not null,
    component_asset_id uuid not null,
    parent_asset_id uuid not null,
    relationship_type varchar(50) not null,
    installed_at timestamp with time zone not null,
    installed_by varchar(100),
    status varchar(50) not null,
    removed_at timestamp with time zone,
    removed_by varchar(100),
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone,
    version bigint
);

create index if not exists ix_asset_installation_tenant_component
    on asset_installation (tenant_id, component_asset_id);
create index if not exists ix_asset_installation_tenant_history
    on asset_installation (tenant_id, component_asset_id, parent_asset_id, installed_at);
create unique index if not exists ux_asset_active_installation
    on asset_installation (tenant_id, component_asset_id)
    where status = 'INSTALLED';
