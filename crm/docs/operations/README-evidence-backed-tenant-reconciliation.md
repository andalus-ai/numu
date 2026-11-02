# CRM evidence-backed tenant ownership reconciliation

## Scope

Migration V16 introduces a proposal ledger for resolving legacy CRM rows whose
`tenant_id` is NULL. It records evidence, independent review, execution
references, and append-only lifecycle snapshots.

It does **not** assign ownership to CRM records. Marking a proposal
`APPLIED` records that an independently executed and reviewed operation was
completed; it does not execute that operation.

## Proposal requirements

Each proposal identifies exactly one CRM aggregate and record UUID, the
proposed tenant UUID, and:

- Evidence type and a stable evidence reference (for example, an approved
  source-system export or signed import manifest).
- SHA-256 digest of the evidence artifact.
- A rationale explaining why that artifact is authoritative for this record.
- The submitting actor identifier.

Only these aggregate names are accepted: `accounts`, `customers`, `leads`,
`opportunities`, `referrals`, `territories`, and
`territory_assignments`.

The evidence reference must resolve to a retained artifact under the
organization's evidence-retention policy. The database stores the reference
and digest; it does not fetch or verify the external artifact.

## Lifecycle

```text
PENDING -> APPROVED -> APPLIED
    |
    +----> REJECTED
```

- A proposal starts as PENDING.
- A reviewer other than the submitter may approve or reject it.
- An approved proposal may be marked APPLIED only with an executor and a
  durable execution/change reference.
- Evidence and proposal identity fields are immutable after submission.
- A proposal cannot be reopened or moved backward.
- Rejected proposals remain in the ledger. A corrected proposal may be
  submitted for the same aggregate/record.
- Only one PENDING, APPROVED, or APPLIED proposal may exist for an aggregate
  record at a time.

The database trigger writes SUBMITTED, APPROVED, REJECTED, and APPLIED
snapshots to `crm_tenant_ownership_proposal_audit`. Ordinary UPDATE and DELETE
operations against the audit table are rejected.

## Operational controls

1. Run the read-only ownership inventory and select a record requiring
   reconciliation.
2. Obtain the source artifact from an independently authoritative system.
   Do not use a relationship match, participant ID, email, name, UUID pattern,
   or inferred parent ownership as proof.
3. Retain the artifact in controlled storage; record its immutable reference
   and SHA-256 digest.
4. Submit a PENDING proposal. The submitting actor must come from the
   application's verified identity context, not a caller-supplied arbitrary
   value.
5. Have a separate authorized reviewer inspect the artifact and rationale.
   Reviewer identity must likewise come from verified authorization context.
6. After approval, prepare a separate reviewed execution change. That change
   must re-check that the target row still exists and still has
   `tenant_id IS NULL`, update only the explicitly approved record/tenant
   pair, and fail closed on any mismatch.
7. In the same controlled execution transaction, record the execution
   reference and transition the proposal to APPLIED. Verify the audit snapshot
   and rerun the ownership inventory.

## Important limitations

- V16 records actor identifiers but does not authenticate them. The
  application/operations layer must derive them from verified identity and
  enforce authorization.
- Database owners/superusers can disable triggers or alter tables. Restrict
  DDL and direct DML privileges to trusted migration/operations roles.
- This milestone does not provide an HTTP API, UI, automatic backfill, or
  stored procedure that mutates tenant ownership.
- Global uniqueness constraints are intentionally unchanged pending a
  separate duplicate and ownership review.
