-- V6__crm_territories.sql
create table if not exists crm_territories (
    id uuid primary key,
    name varchar(200) not null,
    description varchar(2000),
    parent_territory_id uuid,
    territory_active boolean not null default false,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone,
    version bigint
);

create index if not exists idx_territory_parent on crm_territories (parent_territory_id);
create index if not exists idx_territory_active on crm_territories (territory_active);

create table if not exists crm_territory_assignments (
    id uuid primary key,
    territory_id uuid not null,
    account_id uuid not null,
    origin varchar(30) not null,
    status varchar(20) not null,
    assigned_by_user_id uuid not null,
    assigned_at timestamp with time zone not null,
    ended_at timestamp with time zone,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone,
    version bigint
);

create index if not exists idx_assignment_territory on crm_territory_assignments (territory_id);
create index if not exists idx_assignment_account on crm_territory_assignments (account_id);
create index if not exists idx_assignment_status on crm_territory_assignments (status);
