-- Asset hierarchy integrity (ASSET-17)
-- One hierarchical parent per (tenant, source) + supporting index for
-- direct-component lookups by target.

create unique index if not exists ux_asset_hierarchical_parent
    on asset_relationship (tenant_id, source_asset_id)
    where relationship_type in
        ('COMPONENT_OF', 'INSTALLED_ON', 'ATTACHED_TO', 'COMPONENT', 'PARENT', 'CHILD');

create index if not exists idx_asset_relationship_target
    on asset_relationship (tenant_id, related_asset_id);
