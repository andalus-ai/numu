# Numou Omnichannel — Milestone 11: Inbound Ingestion Orchestration

## Goal
Compose provider-neutral verification, durable inbox deduplication, encrypted payload storage, and the atomic verification transition into a single application service.

## Pipeline
1. Resolve and authorize tenant/provider-account routing before constructing the request.
2. Select a configured provider verifier; reject unknown providers.
3. Verify signature/replay policy before claiming an inbox identity.
4. Hash the exact raw request bytes and atomically claim the scoped tenant/provider/account/external-event identity.
5. Return duplicate or payload-conflict outcomes without persisting a second payload.
6. Persist payload through the configured InboundPayloadStore.
7. Atomically transition the inbox row to VERIFIED with its opaque payload reference.
8. Leave business dispatch to a leased worker and existing Numou application use cases.

## Included
- InboundWebhookRequest with defensive copying of body bytes and immutable headers.
- Payload-free InboundIngestionResult and explicit dispositions.
- InboundWebhookIngestionService configured with provider verifier implementations, inbox, payload store, verification store, clock, and finite retention duration.
- Best-effort deletion of a newly stored payload if the durable verification transition fails.
- Unit tests for verification rejection, accepted flow, duplicate short-circuit, and cleanup on transition failure.

## Failure and recovery
- A storage failure after the inbox claim leaves a RECEIVED row, which is not eligible for worker dispatch. A reconciliation/re-drive policy is required for these rows.
- If verification transition fails, the service attempts to delete the payload. Retention cleanup is the backstop if deletion fails.
- The service does not log request bodies, headers, or signature values and does not dispatch business commands.
- Provider-specific signature formats, timestamp windows, secret rotation, request-size limits, tenant authorization, and HTTP response mapping belong in provider/transport adapters.
- Dispatch remains at-least-once and must use existing application idempotency mechanisms.

## Validation
Unit tests are authored, but Maven reactor and PostgreSQL concurrency/integration tests have not been run in this session.
