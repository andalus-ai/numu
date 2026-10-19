-- V3__crm_customers.sql
create table if not exists crm_customers (
    id uuid primary key,
    customer_number varchar(50),
    company_name varchar(100),
    first_name varchar(50),
    last_name varchar(50),
    email varchar(100),
    phone varchar(20),
    address varchar(255),
    city varchar(50),
    state varchar(50),
    postal_code varchar(20),
    country varchar(50),
    industry varchar(50),
    website varchar(100),
    tax_id varchar(50),
    currency_code varchar(3),
    payment_terms varchar(50),
    credit_limit varchar(50),
    account_status varchar(20),
    notes varchar(2000),
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone,
    version bigint
);

create unique index if not exists uq_customer_number on crm_customers (customer_number);
create index if not exists idx_customer_email on crm_customers (email);
create index if not exists idx_customer_number on crm_customers (customer_number);
create index if not exists idx_customer_company on crm_customers (company_name);

create table if not exists crm_customer_contacts (
    customer_id uuid not null,
    contact_id varchar(255),
    first_name varchar(50),
    last_name varchar(50),
    email varchar(100),
    phone varchar(20),
    job_title varchar(100),
    department varchar(50),
    is_primary boolean,
    is_active boolean
);

create index if not exists idx_customer_contacts_customer on crm_customer_contacts (customer_id);

create table if not exists crm_customer_addresses (
    customer_id uuid not null,
    address_id varchar(255),
    address_type varchar(10),
    address varchar(255),
    city varchar(50),
    state varchar(50),
    postal_code varchar(20),
    country varchar(50)
);

create index if not exists idx_customer_addresses_customer on crm_customer_addresses (customer_id);
