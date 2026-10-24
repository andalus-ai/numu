# Schedule-change outbox dead-letter recovery

M34 adds an explicit, auditable operator recovery path for schedule-change events that exhausted the ten-attempt relay policy.

## Endpoint

`POST /internal/operations/planning/outbox/{outboxId}/replay`

Request JSON:

```json
{
  "reason": "Broker credentials were repaired; replay approved by operations"
}
```

The endpoint requires the `platform-operations` role. The replay actor is derived from the authenticated principal; callers cannot submit an arbitrary actor identity. The host application must configure authentication and map trusted operators to the role. Keep the route behind an internal listener or authenticated administrative gateway.

## Safety behavior

- Only an unpublished row explicitly marked dead-lettered can be replayed.
- The replay transition locks the row and atomically resets the attempt count, makes the event due immediately, clears the prior delivery error, and removes the dead-letter marker.
- The same transaction records an audit entry with the outbox ID, authenticated actor, operator-provided reason, replay time, and prior attempt count.
- A second replay request for an event that is no longer dead-lettered is rejected; already-published events cannot be replayed through this operation.
- The reason is required and limited to 500 characters. The actor name is limited to 200 characters.
- Successful requests return HTTP 202 and the event is queued for the normal relay. The endpoint does not publish directly.

Replay is an operational decision, not proof that the underlying transport or consumer issue is fixed. Delivery remains at-least-once; consumers must deduplicate by the stable outbox ID. Replaying an event can cause another delivery attempt, so operators should verify the root cause and downstream idempotency before using it.

## Verification

The integration suite now covers audited replay, rejection of a duplicate replay, re-eligibility for normal relay delivery, invalid operator/reason input, and concurrent worker claims. Run the planning PostgreSQL adapter's Maven tests against PostgreSQL before production rollout; tests have not been executed by this change authoring session.
