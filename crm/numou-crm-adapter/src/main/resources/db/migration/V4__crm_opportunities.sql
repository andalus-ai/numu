-- V4__crm_opportunities.sql
create table if not exists crm_opportunities (
    id uuid primary key,
    name varchar(255) not null,
    description varchar(2000),
    customer_id uuid,
    customer_name varchar(100),
    stage varchar(255) not null,
    estimated_value double precision,
    probability double precision,
    weighted_value double precision,
    currency_code varchar(3),
    assigned_to uuid,
    expected_close_date timestamp with time zone,
    lead_source varchar(50),
    product_interest varchar(255),
    competitors varchar(500),
    decision_criteria varchar(500),
    next_step varchar(255),
    notes varchar(2000),
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone,
    version bigint
);

create index if not exists idx_opp_customer on crm_opportunities (customer_id);
create index if not exists idx_opp_stage on crm_opportunities (stage);
create index if not exists idx_opp_assigned on crm_opportunities (assigned_to);

create table if not exists crm_opportunity_activities (
    opportunity_id uuid not null,
    activity_type varchar(50),
    description varchar(500),
    performed_by varchar(100),
    outcome varchar(200),
    activity_date timestamp with time zone
);

create index if not exists idx_opp_activities_opp on crm_opportunity_activities (opportunity_id);
