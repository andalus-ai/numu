-- V7__crm_outbox.sql
create table if not exists crm_outbox (
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

create index if not exists idx_crm_outbox_unprocessed on crm_outbox (processed, created_at);
