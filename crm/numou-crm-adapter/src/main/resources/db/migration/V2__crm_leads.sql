-- V2__crm_leads.sql
create table if not exists crm_leads (
    id uuid primary key,
    first_name varchar(50) not null,
    last_name varchar(50) not null,
    email varchar(100),
    phone varchar(20),
    company varchar(100),
    job_title varchar(100),
    industry varchar(50),
    source varchar(50),
    status varchar(255) not null,
    assigned_to uuid,
    notes varchar(2000),
    score double precision,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone,
    version bigint
);

create index if not exists idx_lead_email on crm_leads (email);
create index if not exists idx_lead_status on crm_leads (status);
create index if not exists idx_lead_assigned on crm_leads (assigned_to);

create table if not exists crm_lead_activities (
    lead_id uuid not null,
    activity_type varchar(50),
    description varchar(500),
    performed_by varchar(100),
    outcome varchar(200),
    activity_date timestamp with time zone
);

create index if not exists idx_lead_activities_lead on crm_lead_activities (lead_id);
