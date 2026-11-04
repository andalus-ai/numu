# Revenue and Spend Management — M1.3 POS Transaction and Source Readiness

- Status: source and adapter-integration review; one PostgreSQL integration test added; no production behavior changed.
- Date: 2026-11-01.
- Branch: `finance/revenue-spend-m1-contract-architecture`.

## 1. Transaction boundary — verified at adapter integration level

The earlier M1.2 report left POS JTA enlistment as an open question. The following implementation and integration-test evidence narrows that uncertainty:

- `PostgresSaleCommandIdempotency.execute` is annotated `@Transactional` and is the outer durable command boundary.
- It reserves the idempotency key, invokes the supplied business command, and persists the completed result within that method.
- `PostgresSaleRepository.create/update` and `PostgresPosOutboxWriter.append` obtain connections from the configured datasource and are annotated `@Transactional`.
- `PosPostgresPersistenceTest.outboxAndSaleWritesRollbackTogetherWhenIdempotentCommandFails` invokes the idempotency boundary, writes a sale and outbox row through the adapters, then throws. It asserts that the sale, outbox event and idempotency reservation are all absent afterward.
- `changedRequestFingerprintIsRejectedAndFailedCommandRollsBackSaleAndReservation` independently verifies rollback of sale mutation and idempotency reservation.
- The tests run as `@QuarkusTest` against the POS PostgreSQL test resource.

**Conclusion:** the POS PostgreSQL adapters have integration-test evidence that sale, outbox and idempotency writes enlist in one JTA transaction when executed inside the idempotency adapter's transactional boundary. This is stronger than the port comments alone.

**Follow-up test added:** `PosPostgresPersistenceTest.saleCommandServiceCompletionRollsBackSaleOutboxAndIdempotencyWhenAppendFails` now constructs the actual `SaleCommandService` with the PostgreSQL sale/idempotency adapters and a writer that appends through the real PostgreSQL outbox adapter before deliberately failing. It asserts the completed-sale mutation, completed event and idempotency row all roll back, while the previously committed open sale and line remain. This test has been committed but has not yet been executed.

**Remaining limitation:** this integration test invokes the application service directly with production persistence adapters; it does not exercise the complete production HTTP/CDI request composition. The application service itself does not start a transaction; callers must enter through the transactional idempotency adapter.

## 2. Event contract — still not a financial fact

The completed event mapper and tests confirm schema version 1 and the current operational payload. The payload still contains no financial amounts or payment/settlement references.

The Sale aggregate and SaleLine model confirm:
- line snapshots contain quantity and unit price;
- extended amount is unrounded;
- sale subtotal is unrounded;
- tax and discount policy are outside the aggregate;
- sale completion is separate from payment and accounting.

The POS tree inspected for this milestone contains no dedicated checkout/pricing/tax/discount/payment contract that can authoritatively finalize a sale financial snapshot. This is a bounded source-tree finding, not proof that no such contract exists elsewhere in the wider repository.

**Decision:** do not modify `pos.sale.completed` to imply a financial finalization, and do not introduce a second pricing or tax engine in Revenue. A separate source-owned checkout/pricing capability must define the authoritative price calculation and immutable result before Revenue consumes it.

## 3. Required future financial snapshot contract

When a source-owned pricing/checkout flow exists, its finalized fact should be versioned and immutable and carry at least:

- tenant ID and sale/source aggregate ID;
- stable source revision or finalized snapshot ID;
- currency;
- line-level quantity and priced amounts, where legally and operationally required;
- subtotal basis and explicit discount breakdown;
- tax breakdown with jurisdiction/rate/category identifiers where applicable;
- gross payable and net-of-tax amount, with clearly defined semantics;
- rounding mode and precision applied;
- pricing/tax policy version or reference;
- finalized-at timestamp;
- correlation/causation and source event identity.

Do not label any one of these amounts as recognized revenue without an accounting/revenue-recognition policy. Payment authorization, capture, refund and settlement must remain separate facts with their own provider references and idempotency.

Completed-sale refund/return/reversal also needs a source-owned compensating lifecycle fact linked to the original finalized snapshot. The current open-sale void event is not a substitute.

## 4. Next implementation gates

1. Add a POS service-level PostgreSQL integration test that invokes the production command composition and proves rollback of sale, outbox and idempotency writes when the command fails after event append.
2. Identify or establish the source-owned checkout/pricing boundary. Do not add speculative totals to the Sale aggregate.
3. Define the financial snapshot and compensating-event schema only after amount semantics and pricing ownership are explicit.
4. Keep Revenue integration blocked until the snapshot is authoritative, tenant-scoped, versioned and replay-safe.

## 5. Verification status

- [x] Read repository `AGENTS.md` and POS architecture/source files.
- [x] Inspect POS idempotency adapter transaction boundary.
- [x] Inspect POS sale and outbox PostgreSQL adapters.
- [x] Inspect PostgreSQL integration tests for rollback across sale, outbox and idempotency writes.
- [x] Confirm current completed event is operational, not financially complete.
- [ ] Run the POS adapter Maven test reactor and confirm the new rollback test passes.
- [x] Add service-level PostgreSQL rollback integration test using the actual SaleCommandService and production persistence adapters (execution pending).
- [ ] Exercise the complete production HTTP/CDI request composition.
- [ ] Establish authoritative source-owned financial snapshot contract.
- [ ] Define completed-sale refund/reversal facts.
- [ ] Begin Revenue projection only after the above gates pass.
