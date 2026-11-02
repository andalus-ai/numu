-- V16: evidence-backed CRM tenant ownership reconciliation proposals.
--
-- This migration records proposals and their review/execution history. It
-- deliberately does not update CRM aggregate tenant_id columns. Applying an
-- approved proposal remains a separately reviewed operational action.

create table crm_tenant_ownership_proposals (
    proposal_id uuid primary key,
    aggregate_name text not null
        check (aggregate_name in (
            'accounts',
            'customers',
            'leads',
            'opportunities',
            'referrals',
            'territories',
            'territory_assignments'
        )),
    record_id uuid not null,
    proposed_tenant_id uuid not null,
    evidence_type text not null check (length(btrim(evidence_type)) > 0),
    evidence_reference text not null check (length(btrim(evidence_reference)) > 0),
    evidence_sha256 text not null
        check (length(evidence_sha256) = 64 and evidence_sha256 ~ '^[0-9a-fA-F]{64}
    rationale text not null check (length(btrim(rationale)) > 0),
    submitted_by text not null check (length(btrim(submitted_by)) > 0),
    submitted_at timestamptz not null default clock_timestamp(),
    status text not null default 'PENDING'
        check (status in ('PENDING', 'APPROVED', 'REJECTED', 'APPLIED')),
    reviewed_by text,
    reviewed_at timestamptz,
    review_note text,
    applied_by text,
    applied_at timestamptz,
    execution_reference text,
    constraint ck_crm_ownership_proposal_review_state check (
        (status = 'PENDING'
            and reviewed_by is null and reviewed_at is null
            and applied_by is null and applied_at is null
            and execution_reference is null)
        or
        (status in ('APPROVED', 'REJECTED')
            and reviewed_by is not null and length(btrim(reviewed_by)) > 0
            and reviewed_at is not null
            and applied_by is null and applied_at is null
            and execution_reference is null)
        or
        (status = 'APPLIED'
            and reviewed_by is not null and reviewed_at is not null
            and applied_by is not null and length(btrim(applied_by)) > 0
            and applied_at is not null
            and execution_reference is not null
            and length(btrim(execution_reference)) > 0)
    ),
    constraint ck_crm_ownership_proposal_reviewer_distinct check (
        reviewed_by is null or reviewed_by <> submitted_by
    )
);

-- A record may have only one live proposal at a time. Rejected proposals are
-- retained as history and do not block a corrected submission.
create unique index uq_crm_ownership_proposal_live_record
    on crm_tenant_ownership_proposals (aggregate_name, record_id)
    where status in ('PENDING', 'APPROVED', 'APPLIED');

create index idx_crm_ownership_proposals_status_submitted
    on crm_tenant_ownership_proposals (status, submitted_at);

create table crm_tenant_ownership_proposal_audit (
    audit_id bigint generated always as identity primary key,
    proposal_id uuid not null
        references crm_tenant_ownership_proposals (proposal_id),
    event_type text not null
        check (event_type in ('SUBMITTED', 'APPROVED', 'REJECTED', 'APPLIED')),
    actor_id text not null check (length(btrim(actor_id)) > 0),
    occurred_at timestamptz not null default clock_timestamp(),
    proposal_snapshot jsonb not null
);

create index idx_crm_ownership_proposal_audit_proposal
    on crm_tenant_ownership_proposal_audit (proposal_id, occurred_at);

create function crm_record_tenant_ownership_proposal_audit()
returns trigger
language plpgsql
as $$
declare
    event_name text;
    actor_name text;
begin
    if tg_op = 'INSERT' then
        if new.status <> 'PENDING' then
            raise exception 'Ownership proposals must begin in PENDING state';
        end if;
        event_name := 'SUBMITTED';
        actor_name := new.submitted_by;
    else
        if new.proposal_id is distinct from old.proposal_id
           or new.aggregate_name is distinct from old.aggregate_name
           or new.record_id is distinct from old.record_id
           or new.proposed_tenant_id is distinct from old.proposed_tenant_id
           or new.evidence_type is distinct from old.evidence_type
           or new.evidence_reference is distinct from old.evidence_reference
           or new.evidence_sha256 is distinct from old.evidence_sha256
           or new.rationale is distinct from old.rationale
           or new.submitted_by is distinct from old.submitted_by
           or new.submitted_at is distinct from old.submitted_at then
            raise exception 'Ownership proposal evidence and identity are immutable';
        end if;

        if old.status = 'PENDING' and new.status = 'APPROVED' then
            event_name := 'APPROVED';
            actor_name := new.reviewed_by;
        elsif old.status = 'PENDING' and new.status = 'REJECTED' then
            event_name := 'REJECTED';
            actor_name := new.reviewed_by;
        elsif old.status = 'APPROVED' and new.status = 'APPLIED' then
            event_name := 'APPLIED';
            actor_name := new.applied_by;
        else
            raise exception 'Invalid ownership proposal transition: % -> %',
                old.status, new.status;
        end if;
    end if;

    insert into crm_tenant_ownership_proposal_audit
        (proposal_id, event_type, actor_id, proposal_snapshot)
    values
        (new.proposal_id, event_name, actor_name, to_jsonb(new));

    return new;
end;
$$;

create trigger trg_crm_tenant_ownership_proposal_audit
after insert or update on crm_tenant_ownership_proposals
for each row execute function crm_record_tenant_ownership_proposal_audit();

-- Audit records are append-only through ordinary DML.
create function crm_reject_tenant_ownership_audit_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'CRM tenant ownership proposal audit is append-only';
end;
$$;

create trigger trg_crm_tenant_ownership_audit_immutable
before update or delete on crm_tenant_ownership_proposal_audit
for each row execute function crm_reject_tenant_ownership_audit_mutation();
),
    rationale text not null check (length(btrim(rationale)) > 0),
    submitted_by text not null check (length(btrim(submitted_by)) > 0),
    submitted_at timestamptz not null default clock_timestamp(),
    status text not null default 'PENDING'
        check (status in ('PENDING', 'APPROVED', 'REJECTED', 'APPLIED')),
    reviewed_by text,
    reviewed_at timestamptz,
    review_note text,
    applied_by text,
    applied_at timestamptz,
    execution_reference text,
    constraint ck_crm_ownership_proposal_review_state check (
        (status = 'PENDING'
            and reviewed_by is null and reviewed_at is null
            and applied_by is null and applied_at is null
            and execution_reference is null)
        or
        (status in ('APPROVED', 'REJECTED')
            and reviewed_by is not null and length(btrim(reviewed_by)) > 0
            and reviewed_at is not null
            and applied_by is null and applied_at is null
            and execution_reference is null)
        or
        (status = 'APPLIED'
            and reviewed_by is not null and reviewed_at is not null
            and applied_by is not null and length(btrim(applied_by)) > 0
            and applied_at is not null
            and execution_reference is not null
            and length(btrim(execution_reference)) > 0)
    ),
    constraint ck_crm_ownership_proposal_reviewer_distinct check (
        reviewed_by is null or reviewed_by <> submitted_by
    )
);

-- A record may have only one live proposal at a time. Rejected proposals are
-- retained as history and do not block a corrected submission.
create unique index uq_crm_ownership_proposal_live_record
    on crm_tenant_ownership_proposals (aggregate_name, record_id)
    where status in ('PENDING', 'APPROVED', 'APPLIED');

create index idx_crm_ownership_proposals_status_submitted
    on crm_tenant_ownership_proposals (status, submitted_at);

create table crm_tenant_ownership_proposal_audit (
    audit_id bigint generated always as identity primary key,
    proposal_id uuid not null
        references crm_tenant_ownership_proposals (proposal_id),
    event_type text not null
        check (event_type in ('SUBMITTED', 'APPROVED', 'REJECTED', 'APPLIED')),
    actor_id text not null check (length(btrim(actor_id)) > 0),
    occurred_at timestamptz not null default clock_timestamp(),
    proposal_snapshot jsonb not null
);

create index idx_crm_ownership_proposal_audit_proposal
    on crm_tenant_ownership_proposal_audit (proposal_id, occurred_at);

create function crm_record_tenant_ownership_proposal_audit()
returns trigger
language plpgsql
as $$
declare
    event_name text;
    actor_name text;
begin
    if tg_op = 'INSERT' then
        if new.status <> 'PENDING' then
            raise exception 'Ownership proposals must begin in PENDING state';
        end if;
        event_name := 'SUBMITTED';
        actor_name := new.submitted_by;
    else
        if new.proposal_id is distinct from old.proposal_id
           or new.aggregate_name is distinct from old.aggregate_name
           or new.record_id is distinct from old.record_id
           or new.proposed_tenant_id is distinct from old.proposed_tenant_id
           or new.evidence_type is distinct from old.evidence_type
           or new.evidence_reference is distinct from old.evidence_reference
           or new.evidence_sha256 is distinct from old.evidence_sha256
           or new.rationale is distinct from old.rationale
           or new.submitted_by is distinct from old.submitted_by
           or new.submitted_at is distinct from old.submitted_at then
            raise exception 'Ownership proposal evidence and identity are immutable';
        end if;

        if old.status = 'PENDING' and new.status = 'APPROVED' then
            event_name := 'APPROVED';
            actor_name := new.reviewed_by;
        elsif old.status = 'PENDING' and new.status = 'REJECTED' then
            event_name := 'REJECTED';
            actor_name := new.reviewed_by;
        elsif old.status = 'APPROVED' and new.status = 'APPLIED' then
            event_name := 'APPLIED';
            actor_name := new.applied_by;
        else
            raise exception 'Invalid ownership proposal transition: % -> %',
                old.status, new.status;
        end if;
    end if;

    insert into crm_tenant_ownership_proposal_audit
        (proposal_id, event_type, actor_id, proposal_snapshot)
    values
        (new.proposal_id, event_name, actor_name, to_jsonb(new));

    return new;
end;
$$;

create trigger trg_crm_tenant_ownership_proposal_audit
after insert or update on crm_tenant_ownership_proposals
for each row execute function crm_record_tenant_ownership_proposal_audit();

-- Audit records are append-only through ordinary DML.
create function crm_reject_tenant_ownership_audit_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'CRM tenant ownership proposal audit is append-only';
end;
$$;

create trigger trg_crm_tenant_ownership_audit_immutable
before update or delete on crm_tenant_ownership_proposal_audit
for each row execute function crm_reject_tenant_ownership_audit_mutation();
