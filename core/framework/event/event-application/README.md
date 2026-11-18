# Controlled dead-letter replay

The shared event application provides `DeadLetterReplayService` as an
application-level use case. It is intentionally not an HTTP endpoint and does
not assume a broker or persistence vendor.

Composition requires four ports:

- `DeadLetterReplayAuthorizationPort`: authorize actor + tenant + DLQ record.
- `DeadLetterReplayStorePort`: atomically audit and acquire a fenced replay
  lease, report already-completed/in-progress claims, and persist completion or
  failure. Implementations must retain attempt history and reject stale claim
  tokens.
- `EventSerializerPort`: deserialize the original versioned event envelope.
- `EventPublisherPort`: republish the original envelope.

The command must be built from a trusted dead-letter reader/inspection adapter.
Do not accept arbitrary payloads from a public request body. The service checks
that the serialized event type matches the broker event-type header and that
the envelope tenant matches the authorized tenant. It never creates a new
event ID or rewrites original metadata. The operator actor and reason are
recorded separately in the replay audit request.

A replay is at-least-once. If publication succeeds but audit completion fails,
the store lease remains in progress until its lease/recovery policy resolves
the ambiguous outcome. Consumers must retain inbox/idempotency protection
using the original event ID. A store implementation must make
`beginReplay` atomic per (tenant, DLQ record ID), and `completeReplay` /
`failReplay` must be fenced by the claim token. A failed attempt may be
explicitly retried, but completed records are immutable.

No concrete production store or authenticated operator endpoint is supplied
by this milestone; those are deployment-specific adapters and must be added
before enabling replay in a production environment.
