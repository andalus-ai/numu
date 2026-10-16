create table if not exists crm_accounts (
    id uuid primary key,
    participant_id uuid not null,
    parent_account_id uuid null references crm_accounts(id),
    name varchar(200) not null,
    status varchar(20) not null,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone,
    version bigint
);

create index if not exists idx_crm_account_participant
    on crm_accounts (participant_id);
