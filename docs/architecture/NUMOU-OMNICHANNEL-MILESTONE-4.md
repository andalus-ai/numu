# Numou Omnichannel — M4: Inbound Event Intake Contracts

## Scope

M4 adds provider-neutral contracts for inbound provider events and their durable-inbox lifecycle. It deliberately does not claim a durable inbox implementation: storage, transaction guarantees, signature verification and queue/broker integration belong in adapter/runtime modules.

## Added contracts

- `InboundEventEnvelope`: trusted channel context, provider-scoped external event ID, event type, payload SHA-256 fingerprint, local receive time and optional provider occurrence time.
- `InboundDeliveryState`: explicit lifecycle from received through verification and processing, with rejection, retryable failure and dead-letter outcomes.
- Unit tests cover metadata normalization, digest validation, external ID bounds, valid retries and terminal states.

## Invariants

1. Provider event IDs are external identifiers; the persistence key must also include tenant and provider account (and normally provider/connector identity).
2. Duplicate event ID + same payload fingerprint is a replay candidate; duplicate event ID + different fingerprint is a conflict requiring quarantine/alerting, not silent overwrite.
3. A delivery state transition must be atomic. This enum's `canTransitionTo` check is a domain guard, not a concurrency guarantee.
4. Verify authenticity before business dispatch. Parsing a payload or constructing an envelope does not prove signature validity or tenant authorization.
5. Do not log raw payloads or credentials by default. Apply payload retention, redaction and access policies in the inbox adapter.
6. Processing must call owning Numou application ports. The inbox must not directly mutate commerce state.
7. Retries must be bounded and scheduled durably; terminal failures need operator-visible inspection and safe replay procedures.

## Explicitly out of scope

- Database schema/repository, inbox transactions, outbox, broker consumers, provider signature algorithms, HTTP endpoints, dead-letter UI, scheduled retries.
- Marketplace/payment connector implementations.
- End-to-end order orchestration: concrete commerce application ports must be mapped first.

## Acceptance criteria for runtime implementation

- Atomic insert-or-deduplicate by tenant/provider account/external event ID.
- Same event ID and same digest returns the existing delivery; same ID with a different digest is quarantined as a conflict.
- Signature verification and account-to-tenant/branch resolution precede dispatch.
- State transitions and attempt increments are persisted atomically.
- Retry scheduling is bounded and observable; dead-letter replay is authorized and audited.
- Contract tests cover concurrent duplicates, conflicting payloads, invalid signatures, invalid tenant mapping, transient downstream errors, permanent validation errors and replay.
- Full Maven reactor and CI pass before a provider connector is declared production-ready.

## Validation status

Source and unit tests have been added. Tests and the Maven reactor have not been run in this environment; CI must validate Java/compiler compatibility, the foundation API and the complete module graph before merge.
