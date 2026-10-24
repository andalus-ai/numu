# Numou Omnichannel — Milestone 12: Leased inbound dispatch

## Scope

M12 connects the durable worker lifecycle to verified payload references and the provider-neutral application dispatcher. It does not add provider-specific webhook handlers or write into commerce domain tables.

## Processing path

1. Claim one eligible event with a bounded lease and unique lease token.
2. Read payload_reference, verified_at, and provider occurrence time only when the same row remains PROCESSING under that exact lease token.
3. Rebuild ChannelContext from durable, server-resolved inbox metadata.
4. Load the payload through InboundPayloadStore with tenant context and verify its SHA-256 against the durable inbox digest.
5. Dispatch VerifiedInboundEvent through InboundEventDispatcher, whose implementation must call existing Numou application use cases/ports.
6. Mark the row PROCESSED only if the lease token still matches. On a sanitized failure, record a retry deadline or let the store move an exhausted item to DEAD_LETTER.

## Delivery guarantees and failure boundaries

- The inbox claim and lifecycle updates are durable and lease-token fenced.
- This is at-least-once dispatch, not exactly-once business execution. A worker can crash after a downstream side effect and before markProcessed; the event can be retried.
- Dispatchers must pass a stable idempotency key derived from the full tenant/provider/account/external-event identity to the target application use case where supported. Existing business modules remain the source of truth.
- A lease can expire during a downstream call. Fencing prevents stale completion/failure writes but cannot cancel an already-running side effect.
- Payload absence, digest mismatch, missing leased metadata, and dispatcher exceptions produce sanitized error codes; arbitrary exception messages and payload bytes are not persisted as error codes.
- Retry delay is fixed per worker instance in this milestone. Production deployments should add bounded exponential backoff with jitter, concurrency controls, metrics, and operational dead-letter replay tooling.

## Validation status

Unit tests cover success, digest mismatch, dispatcher failure, and stale lease completion. PostgreSQL integration tests and a full Maven reactor build must pass before production deployment.
