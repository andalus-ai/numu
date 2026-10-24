# Numou Omnichannel — Milestone 18: Reliability Release Gate

## Goal

Treat inbound channel delivery as a single reliability boundary from provider request through verified durable inbox, leased dispatch, retry, and audited dead-letter recovery. This milestone adds a repeatable release gate and operational runbook; it does not claim a deployment is production-ready until the required integration checks have actually run in CI.

## Reliability invariants

1. **Verify before accepting.** Unknown providers and invalid signatures are rejected before inbox claims or payload persistence.
2. **Identity is tenant-scoped.** Deduplication identity includes tenant, provider, provider account, and external event ID. Same identity with different payload digest is a conflict, not a duplicate.
3. **No dispatch before verification.** Only events with durable verified metadata may be claimed by the worker. Recovery must quarantine incomplete ingestion, never dispatch it.
4. **Payload integrity.** The payload digest is checked before dispatch. Payloads and secrets must not be copied into logs, metric labels, or dead-letter summaries.
5. **Lease fencing.** Completion/failure updates require the exact lease token. An expired worker must not overwrite a newer lease's state.
6. **At-least-once delivery.** A lease can expire after a downstream side effect. Downstream application use cases must enforce idempotency atomically with their business effect; the platform does not promise exactly-once execution.
7. **Audited recovery.** Replay is authorized per event, tenant-scoped, bounded, and persisted with the state transition and audit record in one transaction.
8. **One authoritative business state.** Channel adapters call existing application ports; they do not write directly to commerce, inventory, payment, fulfillment, or finance tables.

## Validation matrix

Run this matrix for changes to inbound ingestion, persistence, dispatch, retries, payload storage, or replay.

| Boundary | Required case | Expected result |
|---|---|---|
| Provider verifier | invalid signature / stale timestamp | rejected; no inbox claim or payload persistence |
| Provider routing | unknown provider ID | safe rejection; no state mutation |
| Inbox identity | exact duplicate delivery | duplicate result; no second dispatch |
| Inbox identity | same event identity, different body digest | conflict result; never silently overwrite |
| Tenant isolation | same provider event ID in two tenants | independent identities and state |
| Payload store | write/read failure | safe failure result; no unverified dispatch |
| Verification transition | persistence failure after payload write | payload cleanup attempted; no dispatch |
| Worker | payload missing or digest mismatch | retry/dead-letter policy applies; dispatcher not called |
| Worker lease | lease expires and another worker claims | stale token cannot complete or fail newer lease |
| Retry | transient dispatch failure | bounded retry scheduled using configured policy |
| Exhaustion | attempts reach configured maximum | dead-lettered; safe error code only |
| Replay | unauthorized actor | no state mutation and no existence leak |
| Replay | successful replay request | dead-letter transition and audit record are durable together |
| Metrics | arbitrary provider error/message | never becomes a label/dimension |
| Downstream | dispatch retried after uncertain timeout | downstream idempotency prevents duplicate business effect |

Provider-specific contract suites should be implemented beside each provider adapter and exercise the real signature algorithm against official test vectors. Do not add provider SDKs to `numou-omnichannel-core`.

## Maven validation

From the repository root, execute:

```bash
mvn -pl commerce/omnichannel/numou-omnichannel-core -am test
mvn -pl commerce/omnichannel/numou-omnichannel-postgres -am test
mvn -pl commerce/omnichannel/numou-omnichannel-payload-store -am test
```

PostgreSQL integration tests are opt-in and require an isolated test database/schema. Configure `NUMOU_TEST_POSTGRES_URL` (and, where required, `NUMOU_TEST_POSTGRES_USER` / `NUMOU_TEST_POSTGRES_PASSWORD`) in CI. Keep these tests isolated from developer and production schemas. If the environment is not configured, report the PostgreSQL suite as **not run**, not passed.

For a release candidate, run the full relevant reactor build after module tests:

```bash
mvn -pl commerce/omnichannel -am verify
```

Record the exact commit SHA, command, exit code, and whether PostgreSQL-backed cases actually executed. Do not mark this gate complete based only on compilation or an empty workflow-run list.

## Deployment and operations checklist

- Put the ingress endpoint behind TLS, request-size limits, rate limiting, and provider-specific secret rotation.
- Preserve the raw request bytes for signature verification; do not parse and reserialize JSON before verification when the provider signs the raw body.
- Bind replay actor identity to the authenticated host principal. Require a dedicated permission, tenant scope, and audit retention policy.
- Expose metrics through the host's Micrometer/OpenTelemetry adapter using only fixed enums and bounded dimensions. Never use event IDs, tenant IDs, account IDs, URLs, exception messages, or payload fields as metric labels.
- Alert on sustained dead-letter growth, retry age, ingestion/verification failures, payload-store errors, and stale leases. Define thresholds from observed baseline and service objectives rather than hardcoding universal values here.
- Document database migration/rollback, payload retention and encryption keys, replay approval, and incident recovery procedures for each deployment target.
- Exercise restore/recovery in a staging environment before production rollout.

## Exit criteria

- All core unit tests pass.
- PostgreSQL tests run against a real supported PostgreSQL version and pass.
- Provider contract suites cover invalid signatures, replay/timestamp policy, duplicate IDs, conflicting payloads, and tenant/account isolation.
- Host integration demonstrates authentication and per-tenant authorization for operational endpoints.
- Test results and deployment-specific alert thresholds are attached to the release record.

**Status:** This document defines the gate; it does not assert that the gate has been executed. No test pass is implied by adding this document.
