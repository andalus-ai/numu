# Cargo & Courier — Milestone 7: Network Partners and Integrations

## Delivered foundation
- Tenant-owned partner directory for carriers, agents, franchisees and external delivery networks.
- Canonical partner service capabilities and lifecycle statuses.
- Durable external shipment references, preserving Numou's shipment ID as the internal identity.
- Inbound webhook inbox with a tenant + partner + external-event idempotency key, retry/dead-letter state, and payload retention for asynchronous processing.
- Composite tenant foreign keys keep partner references, shipment references and inbox rows inside the same tenant boundary.

## Integration safety contract
- Verify webhook signatures, timestamp/replay windows and partner credentials before constructing a canonical `PartnerWebhookEvent`. The domain object is not an authentication mechanism.
- Store credentials and secrets in a secrets manager; do not store shared secrets or authorization headers in partner records or event payloads.
- Treat all partner payloads as untrusted input. Validate schemas and map them to internal commands; never let a webhook directly mutate shipment state.
- Inbound event uniqueness provides at-most-once inbox insertion for a partner event ID, not exactly-once downstream effects. Processors must use transactional state changes/outbox and record processing outcomes.
- Keep raw personally identifiable delivery data out of payloads where possible; establish retention and redaction policies before production.
- Partner, shipment and event operations must include tenant authorization plus actor/branch policy checks.

## Follow-on production hardening
- Add tenant-explicit JDBC repository methods for partner registration, partner shipment references, and inbox insert/claim/complete/retry.
- Add integration application ports and adapters for carrier-specific API clients; avoid hard-coding provider APIs in the domain.
- Add HMAC verification, replay-window enforcement, outbound webhook delivery and secret rotation through the platform's credential facility.
- Add Testcontainers coverage for tenant isolation, inbox idempotency, concurrent claims, retry and dead-letter transitions. Migration and tests have not been run in this environment.
