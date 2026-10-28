# Notification draft consolidation

The legacy implementation in `notification/draft` is useful as a behavior inventory, not as a production-ready module to copy wholesale.

## Reuse and migration map

| Draft component | Decision | Target boundary |
| --- | --- | --- |
| `NotificationMessage` | Keep the transport idea, replace mutable DTO and implicit current timestamp with explicit application/domain contracts | Notification application + channel adapter DTOs |
| `DeviceRegistrationRequest`, `PushDeviceRecord` | Re-model with tenant and authenticated recipient identity; never accept a client ID as proof of ownership | Notification domain/application |
| `PushDeviceSummary` | Retain the safe-summary principle; never return a raw provider token from management APIs | REST adapter |
| `DeviceRegistrationService` | Replace in-memory map and JSON-file persistence with a repository port and database-backed adapter | Notification application + persistence |
| `FcmPushService`, `PushDispatchService` | Preserve FCM as an optional provider adapter; put delivery behind a channel port, bounded worker, retry policy, and durable delivery state | Notification delivery milestone |
| `WebSocketService` | Keep realtime as an optional channel adapter, not the source of truth; authenticate handshake using host identity integration and avoid bearer tokens in query strings | Realtime transport adapter |
| `NotificationAuthService` | Do not transplant its hand-rolled JWT verification or identifier matching. Use the host's verified principal, tenant resolution, and authorization contract | Host composition/security adapter |
| `NotificationResource` | Split inbox/device endpoints from internal dispatch; validate inputs and map application outcomes to HTTP without exposing exception messages | REST adapter |
| `HealthResource` | Keep operational health separate from public endpoint; expose readiness and provider configuration only to authorized operational probes | Runtime/operations |

## Security and reliability issues to resolve before production

- The current internal-send key check allows sending when no key is configured; production must fail closed.
- JWT claim matching against username/email/subject is not an authorization policy and does not establish tenant membership.
- WebSocket bearer tokens in query parameters can leak into proxy/access logs; use the hosting platform's supported authenticated handshake.
- The JSON-file device registry is process-local, race-prone across instances, and not durable enough for a horizontally scaled service.
- Push dispatch uses unbounded common-pool asynchronous work and returns a target count before delivery results are known.
- Device tokens must never appear in logs, health responses, or ordinary API summaries. Invalid-token pruning must be scoped and auditable.
- A successful push attempt is not proof that a user read the notification. The persisted in-app inbox remains canonical.

## Milestone sequence

1. **N1 — Inbox foundation:** domain, application port/use cases, PostgreSQL persistence and tenant/recipient-scoped idempotency.
2. **N2 — Device registry foundation:** authenticated-owner-scoped device model and persistence contract. This milestone deliberately does not expose a REST endpoint until the host identity adapter is verified.
3. **N3 — Host-authenticated API:** inbox and device endpoints deriving tenant and recipient from a verified host principal, never trusting caller-supplied owner IDs.
4. **N4 — Durable delivery:** delivery records/outbox, bounded worker, retries/backoff, terminal failure state, and provider-neutral channel port.
5. **N5 — Provider adapters:** FCM and optional realtime/email/SMS adapters, secret management, token invalidation and provider callback handling.
6. **N6 — Preferences, templates, observability and retention:** opt-outs by channel/category, localization, metrics, redaction, audit and retention policy.

N2 is intentionally a foundation and does not claim that push delivery or external channel dispatch is production-ready.
