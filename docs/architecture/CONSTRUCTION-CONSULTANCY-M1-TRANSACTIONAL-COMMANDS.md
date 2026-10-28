# Construction Consultancy M1 — Transactional Command

## Scope

Adds the first application write use case: create a consultancy engagement. It uses the existing Construction IdempotentCommandExecutor, requires a trusted tenant/actor context, checks authorization against the client organization, and stores a stable receipt as the idempotency response.

## Transaction contract

The handler writes only through TransactionalCommandContext.consultancy().saveEngagement. This operation must use the same transaction-bound connection as idempotency reservation/completion. The new context method defaults to a fail-closed exception so existing adapters do not silently perform a non-atomic save. A production adapter must override it before the use case can run successfully.

The request fingerprint is calculated server-side from canonical command fields; callers cannot select the fingerprint. Reusing the key with a different fingerprint or operation is rejected by the existing executor. The response receipt avoids serializing the aggregate as an idempotency payload.

## Authorization and tenant safety

The actor ID and tenant ID are carried in the trusted application context and must be sourced from authenticated identity and tenant resolution. Authorization is checked before creating the aggregate. Repository/adapter implementations must still enforce tenant scoping and organization membership; command data is not proof of authority.

## Deliberate limitations

- This is the create use case only; transition handlers and deliverable commands remain future work.
- No persistence adapter, migration, endpoint, or event is added here. The aggregate currently has no consultancy domain event contract to serialize, so this handler does not fabricate an outbox event.
- Tenant-scoped engagement-number uniqueness must be enforced by a database constraint in the adapter.
- This does not establish Indonesian regulatory compliance or Sharia/istisna' compliance.
- Build and tests must pass in CI before this slice is considered integrated.
