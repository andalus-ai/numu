# Consultancy deliverable document evidence boundary

## Ownership

- Construction owns consultancy engagements, deliverables, workflow state, party-level authorization, and the business meaning of evidence relationships.
- Document owns document identity, immutable version identity, binary storage, document metadata, and append-only document-to-aggregate links.
- Construction must use a public application/SPI contract. It must not query Document tables, import adapter entities, or store document bytes.
- New consultancy evidence should pin a specific document version. An unpinned legacy link is readable for compatibility but is not a sufficient basis for asserting which artifact was reviewed.

## Application contract milestone

The Construction SPI defines a narrow `ConsultancyEvidenceLinkStore` port and an immutable read model. The adapter bridge is responsible for translating to the Document capability's public `DocumentLinkRepository` and `DocumentLink` types. The bridge must map a deliverable to a stable aggregate type and use the version-pinned link factory.

## Authorization requirements before wiring a command or query

1. Resolve the deliverable through tenant-scoped Construction persistence.
2. Verify the acting organization is the persisted client or consultant party and authorize the actor for the requested action.
3. Authorize document access separately through Document's supported access policy. Knowing a document UUID is not authorization.
4. On reads, authorize both sides before returning the relationship or allowing a caller to fetch/download the document.
5. Never accept a caller-supplied tenant or actor as trusted identity; runtime wiring must derive them from authenticated context.

## Reliability and partial failure

The current Document link repository is a separate capability boundary. A Construction database transaction does not automatically include a Document write. Do not claim a single atomic transaction across the two capabilities. Before production wiring, select and test one explicit reliability strategy:

- a durable outbox/saga with retry, deduplication, and observable pending/failed states; or
- a supported shared transaction contract where both adapters provably use the same transaction connection.

The first implementation should prefer the outbox/saga when capabilities may deploy independently. A successful command must not be reported until the chosen persistence protocol has reached its defined durable state.

## Audit and evidence semantics

Evidence relationships are append-only. Replacing evidence means adding a new version-pinned relationship; it must not rewrite prior links. The deliverable audit timeline should record the business action and reference identifiers without duplicating document content. Tests must cover tenant isolation, party authorization, document authorization, idempotent retries, cross-tenant/document-version rejection, and recovery from partial failure.

This boundary does not prove a document is digitally signed, legally valid, accepted by a regulator, or Sharia-compliant. Indonesian statutory requirements and Sharia istisna' terms require separate qualified review.

## Authorization port

The application layer has a dedicated `ConsultancyEvidenceAuthorization` contract with separate checks for deliverable access and document/version access. The attach/read orchestration must call both checks; implementing one must never be treated as satisfying the other. Document access requests require a concrete version ID, preventing a workflow from claiming to authorize an unspecified artifact. Runtime adapters must connect it to trusted identity/membership and Document access-policy services before any endpoint is exposed.

## Durable operation contract

`ConsultancyEvidenceOperation` models the saga state machine: `PENDING`, `LINKED`, `RETRYABLE_FAILURE`, and `TERMINAL_FAILURE`. Retryable failures can return to pending; terminal failures cannot be silently retried. The tenant-scoped idempotency key identifies a logical request, while the document version remains explicit on the operation.

`ConsultancyEvidenceOperationStore` is the persistence boundary. The PostgreSQL adapter creates operations idempotently, rejects reuse of a tenant-scoped key for a different immutable request, and claims due work with a single `FOR UPDATE SKIP LOCKED` update. A claim carries a fresh lease token; completion and failure transitions are conditional on that token and a live lease. Long-running work may renew the lease without changing its token. Stale workers receive `false`/an empty renewal result and cannot overwrite a newer attempt.

The adapter is still only the persistence layer. It does not execute the saga, call Document, enforce runtime party/document authorization, or prove cross-capability atomicity. PostgreSQL integration tests for concurrent claims, expired-lease recovery, stale-token rejection, idempotency conflicts, and tenant isolation remain required before production use.
