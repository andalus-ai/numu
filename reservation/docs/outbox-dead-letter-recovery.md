# Reservation outbox dead-letter recovery

## M16 boundary

Reservation owns the outbox lifecycle and the persistence transition from `DEAD`
to `PENDING`. The host application supplies an authorization decision; the
Reservation capability must not infer authorization from possession of an event ID.

The operator workflow is deliberately separate from the publisher/relay:

1. Authorize the actor for the tenant-scoped inspection or recovery action.
2. Read only dead-letter metadata for that tenant. Payload disclosure is a separate,
   more restrictive concern and is not part of the initial inspection contract.
3. Requeue only an event whose current status is `DEAD`.
4. Preserve the original `event_id`; consumers continue to deduplicate on it.
5. Reject `PUBLISHED`, `PENDING`, and `PROCESSING` events.
6. Commit the status transition and its audit record in one PostgreSQL transaction.

## Durable audit schema

Migration `V2__reservation_outbox_recovery_audit.sql` adds
`reservation_outbox_recovery_audit`. It records tenant, original event ID, actor,
operator reason, prior/new states, previous delivery-attempt count, request ID,
and database timestamp. The unique key
`(tenant_id, event_id, request_id)` provides a durable idempotency boundary for
operator commands.

The audit row references the existing outbox event. It does not duplicate the
event payload or create a new event identity. A successful requeue resets the
delivery attempt counter so the configured relay retry budget can be applied
again; the previous count remains in the audit row.

## Required transaction algorithm

The PostgreSQL adapter must execute the following steps on one connection:

- Begin transaction.
- Lock the tenant-scoped outbox row with `SELECT ... FOR UPDATE`.
- Check for an existing audit row with the same tenant, event, and request ID.
  If found, return the recorded result without another transition.
- Require current status `DEAD`; never transition a published event.
- Update that same row to `PENDING`, set `available_at` to database time,
  clear lease fields, and reset attempts to zero.
- Insert the recovery audit row.
- Commit. Any audit insert failure must roll back the state change.

The row lock serializes competing recovery requests. The conditional state check
also prevents a recovery command racing a relay transition from reviving an
already-published event. Tenant scoping is required on every read and write.

## Security and operational requirements

- Authorization must be checked before listing or returning event metadata.
- Actor ID, tenant ID, request ID, and a nonblank reason are mandatory.
- Do not expose payloads or raw SQL errors in the initial operator API.
- Inspection is paginated and ordered deterministically by occurrence time and
  event ID.
- Recovery is an explicit operator action; there is no automatic replay endpoint.
- Audit records are append-only evidence and must not be updated or deleted by
  application code.

## Verification gates

M16 is not complete until PostgreSQL integration tests cover tenant isolation,
unauthorized access, same-request idempotency, concurrent recovery, relay/recovery
races, published-event rejection, and rollback when audit insertion fails. Runtime
HTTP wiring and permission mapping must remain optional composition concerns.
