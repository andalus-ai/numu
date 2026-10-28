# Notification Capability — Architecture and Implementation Roadmap

- **Status:** Proposed implementation contract; not evidence that the capability exists or is complete
- **Repository:** `andalus-ai/numu`
- **Architecture alignment:** `AGENTS.md`, ADR-003 reusable capability modules, and `docs/architecture/DOMAIN-CAPABILITY-MATRIX.md`
- **Scope:** Shared notification orchestration and delivery; domain modules retain ownership of their business rules

## 1. Objective

Introduce a reusable Notification capability that can be composed into a standalone application, modular monolith, distributed services, cloud deployment, or enterprise/on-premises installation. It should serve all Numou domains without making any domain depend on a particular transport provider or deployment topology.

The initial product profile should support Indonesian business operations and localization, while keeping locale, region, provider, and regulatory policy configurable rather than hard-coded.

This roadmap is a target contract. It does not claim that a notification module, provider adapter, UI, migration, or runtime is already implemented.

## 2. Ownership and boundaries

### Notification owns

- Notification intent and recipient resolution through explicit recipient references.
- Notification policies: category, priority, channel eligibility, quiet hours, expiry, and escalation where configured.
- User/tenant delivery preferences and consent/suppression checks where legally or operationally required.
- Template identity, locale selection, versioning, and rendering contracts.
- Channel routing and provider-independent delivery lifecycle.
- Durable delivery attempts, retry scheduling, idempotency, deduplication, and terminal failure visibility.
- In-app inbox entries, read/unread state, dismissal, and user-facing notification history.
- Operational delivery telemetry and audit references without exposing message secrets.

### Notification does not own

- Business truth or lifecycle transitions (for example, whether an invoice is paid, a shipment is delivered, or a reservation is confirmed).
- Payment execution, accounting journals, shipment tracking, workflow approvals, CRM customer records, or domain authorization policy.
- A universal communication campaign/marketing automation model; marketing consent and campaign policy must be explicitly integrated with the appropriate capability.
- Provider credentials, domain persistence entities, or transport-specific logic in domain code.

Business modules publish domain events or invoke a stable notification application contract. Notification consumers must not write into another capability's tables or mutate its aggregates.

## 3. Proposed module decomposition

Confirm exact Maven reactor locations and naming conventions against the repository before adding artifacts. Do not assume a root-level Maven reactor; the repository has multiple reactor roots.

Keep logical roles separate, whether they ultimately become Maven artifacts or packages:

1. **Notification domain** — notification identity, category, priority, lifecycle, delivery state, preference rules, and domain invariants.
2. **Notification application** — request/query contracts, orchestration, recipient resolution ports, template/rendering ports, dispatch ports, and authorization checks.
3. **Notification persistence adapter** — durable notifications, preferences, delivery jobs/attempts, idempotency records, and inbox queries.
4. **Notification runtime/adapters** — selected provider integrations, worker scheduling, HTTP/API integration, and composition wiring.
5. **Optional channel adapters** — email, SMS, WhatsApp Business Platform providers, web push, mobile push, and in-app delivery. Each is independently configurable and replaceable.
6. **Optional presentation adapter** — inbox, unread count, preferences, device registration, and delivery-status views, built only where the repository's UI architecture supports it.

Do not create a separate microservice or one Maven module per channel by default. Begin with the narrowest existing module pattern and make deployment separation an explicit composition choice.

## 4. Public contract

The initial public application contract should express intent, not provider mechanics. Candidate operations:

- `RequestNotification`: request delivery using a stable notification type/template key, tenant and recipient references, typed parameters, optional preferred channels, expiry, correlation reference, and idempotency key.
- `GetNotification` / `ListInboxNotifications`: query a caller-authorized recipient's inbox with cursor pagination and optional unread/category filters.
- `MarkNotificationRead` / `DismissNotification`: update only the caller's own permitted inbox state.
- `GetNotificationPreferences` / `UpdateNotificationPreferences`: manage allowed categories and channels under tenant policy.
- `RegisterDeviceEndpoint` / `RevokeDeviceEndpoint`: optional push-channel contract with ownership checks and secret-safe storage.
- `GetDeliveryStatus`: expose provider-independent status and sanitized failure classification.

Names are proposals, not existing APIs. Final signatures must follow actual repository conventions and avoid leaking persistence entities.

## 5. Core data concepts

Use stable IDs and explicit tenant scope. Candidate records:

- **Notification** — tenant, recipient reference, category/type, priority, template/version reference or rendered snapshot policy, safe payload/reference, created/expiry timestamps, and inbox state.
- **Delivery** — notification reference, channel, destination reference, status, attempt count, next-attempt time, provider message reference, sanitized error classification, and timestamps.
- **DeliveryAttempt** — immutable attempt number, start/end, outcome, provider reference, and redacted diagnostic metadata.
- **NotificationPreference** — tenant and recipient scope, category/channel rules, quiet-hour/time-zone policy, and version/audit metadata.
- **Template** — stable key, locale, version, permitted parameter schema, rendering format, and lifecycle.
- **DeviceEndpoint** — recipient ownership, platform, token reference, status, and timestamps; never expose raw push tokens through normal reads or logs.
- **Outbox/inbox or equivalent delivery record** — durable handoff from committed business state to asynchronous dispatch.

Avoid storing unnecessary sensitive business data in notification bodies. Prefer authorized deep links and stable resource references. A notification is not an authorization grant.

## 6. Delivery lifecycle and reliability

Use an explicit state machine. At minimum, distinguish queued, processing, provider-accepted, delivered (only when the provider can substantiate it), retryable failure, terminal failure, expired, and cancelled. Do not claim end-user delivery merely because an API accepted a request.

Reliability requirements:

- Persist intent before asynchronous dispatch; use the existing supported outbox/event pattern where available.
- Make request handling idempotent within a documented tenant and caller scope.
- Use bounded exponential backoff with jitter, retry classification, expiry/deadline enforcement, and a configurable maximum attempt policy.
- Protect workers with leases or equivalent concurrency control so concurrent workers do not intentionally dispatch the same attempt.
- Assume provider calls can succeed while acknowledgements are lost. Use provider idempotency where available and document residual duplicate-delivery risks where it is not.
- Handle provider callbacks with signature verification, replay protection, tenant/provider correlation, and idempotent state transitions.
- Provide a dead-letter/terminal-failure inspection and redrive path with authorization and audit.
- Never retry permanent invalid-recipient, consent, template, or policy failures as if they were transient infrastructure failures.

## 7. Security, tenancy, privacy, and compliance

- Derive tenant and actor identity from trusted authenticated context; never trust a caller-supplied tenant ID alone.
- Authorize inbox queries and mutations for the recipient and tenant on every request.
- Enforce tenant isolation in persistence queries, unique constraints, background jobs, provider callbacks, exports, and operational tooling.
- Store provider secrets in the configured secret-management mechanism; never commit credentials or log tokens, message bodies, OTPs, or sensitive payloads.
- Redact or minimize personal data in logs and delivery diagnostics; define retention and deletion behavior with audit/legal requirements in mind.
- Keep authentication/security alerts and transactional notices distinct from marketing messages. Consent, opt-out, quiet-hour, and legal policy must be evaluated for the channel and jurisdiction.
- For Indonesia, treat Bahasa Indonesia and Asia/Jakarta as defaults only where the tenant/user has not configured a preference; support other locales and time zones. Validate applicable PDP, electronic-system, telecom, and channel-provider obligations with qualified compliance owners before launch.
- Apply rate limits and abuse controls to user-triggered sends, OTP-like messages, recipient fan-out, and public APIs.
- Validate deep-link destinations and template parameters to prevent injection, unsafe links, and cross-tenant information disclosure.

## 8. Channel adapter contract

Each adapter should implement a provider-neutral port with explicit capabilities, such as submit, classify provider response, and optionally process verified delivery callbacks. Adapter metadata must declare whether it supports provider idempotency, delivery receipts, message expiry, and per-message status lookup.

Initial channel order:

1. **In-app inbox** — first-class baseline and testable without external providers.
2. **Email** — transactional messages through a configurable provider.
3. **Web/mobile push** — only after secure endpoint lifecycle and permission UX exist.
4. **SMS and WhatsApp Business Platform** — provider-backed and policy-gated; template approval, opt-in, pricing, regional availability, and provider constraints remain adapter/configuration concerns.

Do not scrape or automate consumer WhatsApp accounts. Use authorized business messaging integrations and honor platform policy. No provider should be mandatory for the domain module to compile or its unit tests to run.

## 9. Observability and operations

Provide metrics and structured logs for queue age/depth, attempts, accepted/delivered/failed counts, retry volume, expired messages, provider latency/error rates, callback lag, and terminal failures. Tag metrics with bounded dimensions such as channel/provider/status; do not use recipient IDs or notification IDs as metric labels.

Support health/readiness checks, graceful worker shutdown, bounded concurrency, backpressure, provider circuit-breaking where appropriate, configuration validation, and operator-authorized redrive. Keep provider payloads and credentials out of traces.

## 10. Milestones and acceptance criteria

### N0 — Repository discovery and contract

- Inspect current module/reactor conventions, event/outbox infrastructure, identity/tenant contracts, persistence adapters, messaging capability, and existing UI.
- Confirm no existing notification implementation or document why an existing capability should be extended instead.
- Record the selected package/artifact location and dependency graph; update the capability matrix.
- No duplicate event bus, identity system, or generic messaging platform is introduced.

### N1 — In-app notification vertical slice

- Add domain/application contracts, persistence, migration, authorization, and inbox query/read state.
- Add tenant-isolation, recipient-authorization, duplicate-request, concurrency, and lifecycle tests.
- Demonstrate one real producer using a public contract or event adapter without coupling domain code to persistence/runtime infrastructure.
- Add API/UI integration only using established repository patterns.

### N2 — Durable asynchronous delivery

- Add outbox/worker integration, delivery attempts, idempotency, bounded retry, expiry, terminal failure, and redrive.
- Test crash/restart recovery, duplicate event replay, concurrent workers, transient/permanent failures, and tenant isolation.
- Expose delivery status and operational metrics.

### N3 — Templates and preferences

- Add versioned, locale-aware templates, parameter validation, per-category/channel preferences, quiet hours, and preference audit.
- Test fallback locale, invalid templates, opt-out/suppression, time-zone boundaries, and template version changes.
- Ensure rendered payloads cannot reveal data outside the recipient's authorization scope.

### N4 — Provider-backed channels

- Implement and contract-test email, then push/SMS/WhatsApp adapters as separately selectable integrations.
- Verify provider signatures, callback replay, rate limits, timeouts, redaction, and configuration failure behavior.
- Use provider sandbox/test modes where available; do not claim live delivery without an end-to-end verification.

### N5 — Cross-domain adoption and production readiness

- Integrate representative producers from at least two independent capabilities (for example, workspace/task activity and shipment lifecycle), using documented contracts.
- Verify standalone and modular-monolith composition; document the remote adapter path without forcing distributed deployment.
- Complete load/backpressure, retention, recovery, observability, security, migration, API compatibility, and operational runbook checks.
- Update capability matrix and architecture tests based on actual contracts.

## 11. Required test matrix

- Domain unit tests for valid and invalid lifecycle transitions.
- Application tests for authorization, tenant scope, preferences, recipient resolution, and idempotency.
- Persistence integration tests for tenant-scoped uniqueness, rollback, ordering, and concurrent updates.
- Worker tests for retry/backoff, expiry, duplicate replay, lease expiry, crash recovery, and terminal failure.
- Adapter contract tests for timeouts, provider throttling, malformed responses, callback authentication, callback replay, and redaction.
- Architecture tests proving domain code has no dependency on HTTP, persistence, broker, provider SDK, or UI code.
- Migration and compatibility tests for existing records and public API/event contracts.
- At least one consumer test outside the notification capability.

## 12. Definition of done

A milestone is complete only when its code, migrations, contracts, tests, and docs are committed and the relevant reactor/CI results are observed. A roadmap or open PR is not implementation evidence. Do not merge automatically; keep changes in milestone-sized PRs and report verification honestly.
