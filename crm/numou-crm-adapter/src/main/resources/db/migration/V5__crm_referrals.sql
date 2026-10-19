-- V5__crm_referrals.sql
create table if not exists crm_referrals (
    id uuid primary key,
    referrer_account_id uuid not null,
    target varchar(20) not null,
    type varchar(20) not null,
    status varchar(20) not null,
    code varchar(100) not null,
    description varchar(2000),
    referred_at timestamp with time zone,
    accepted_at timestamp with time zone,
    converted_at timestamp with time zone,
    target_lead_id uuid,
    target_opportunity_id uuid,
    external_reference varchar(200),
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone,
    version bigint
);

create index if not exists idx_referral_referrer on crm_referrals (referrer_account_id);
create index if not exists idx_referral_status on crm_referrals (status);
create index if not exists idx_referral_code on crm_referrals (code);
