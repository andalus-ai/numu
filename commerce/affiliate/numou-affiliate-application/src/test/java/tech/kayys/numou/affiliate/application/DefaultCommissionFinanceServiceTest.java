package tech.kayys.numou.affiliate.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.affiliate.domain.*;
import tech.kayys.numou.affiliate.spi.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DefaultCommissionFinanceServiceTest {
    private static final String TENANT = "tenant-a";
    private static final UUID ENTRY_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID PROGRAM = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PARTNER = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");
    private final AffiliateCommandContext context =
            new AffiliateCommandContext(TENANT, "operator-1", "request-1", "corr-1");

    private CommissionEntry entry(CommissionStatus status) {
        return new CommissionEntry(ENTRY_ID, TENANT, PROGRAM, PARTNER, UUID.randomUUID(),
                "commerce.order", "order-1", "paid-1", UUID.randomUUID(), 1, "USD",
                new BigDecimal("100.00"), new BigDecimal("10.00"), CommissionEntryType.EARNING,
                status, null, "commission-key-1", NOW);
    }

    private static class FakeOutbox implements CommissionFinanceOutboxRepository {
        CommissionFinancePosting stored;
        CommissionFinancePosting completed;
        CommissionFinancePosting claimed;
        AtomicInteger enqueueCalls = new AtomicInteger();

        @Override public CompletionStage<CommissionFinancePosting> enqueueOrGet(CommissionFinancePosting posting) {
            enqueueCalls.incrementAndGet();
            if (stored == null) stored = posting;
            return CompletableFuture.completedFuture(stored);
        }
        @Override public CompletionStage<List<ClaimedPosting>> claimPending(
                String tenantId, String workerId, Instant now, Duration leaseDuration, int limit) {
            if (stored == null || !stored.tenantId().equals(tenantId)
                    || stored.status() != CommissionFinancePostingStatus.PENDING) {
                return CompletableFuture.completedFuture(List.of());
            }
            claimed = stored;
            return CompletableFuture.completedFuture(List.of(new ClaimedPosting(stored, "lease-1")));
        }
        @Override public CompletionStage<CommissionFinancePosting> complete(
                String tenant, UUID id, String token, String reference, Instant at) {
            completed = stored.posted(reference, at);
            stored = completed;
            return CompletableFuture.completedFuture(completed);
        }
        @Override public CompletionStage<CommissionFinancePosting> recordRetryableFailure(
                String tenant, UUID id, String token, String reason, Instant at) {
            stored = stored.retryableFailure(reason, at);
            return CompletableFuture.completedFuture(stored);
        }
        @Override public CompletionStage<CommissionFinancePosting> requireReconciliation(
                String tenant, UUID id, String token, String reason, Instant at) {
            stored = stored.reconciliationRequired(reason, at);
            return CompletableFuture.completedFuture(stored);
        }
        @Override public CompletionStage<Optional<CommissionFinancePosting>> findById(
                String tenant, UUID id) {
            return CompletableFuture.completedFuture(stored != null && stored.id().equals(id)
                    && stored.tenantId().equals(tenant) ? Optional.of(stored) : Optional.empty());
        }
        @Override public CompletionStage<Optional<CommissionFinancePosting>> findByIdempotencyKey(
                String tenant, String key) {
            return CompletableFuture.completedFuture(stored != null && stored.tenantId().equals(tenant)
                    && stored.idempotencyKey().equals(key) ? Optional.of(stored) : Optional.empty());
        }
        @Override public CompletionStage<CommissionFinancePosting> reconcilePosted(
                String tenant, UUID id, String reference, Instant at) {
            stored = stored.posted(reference, at);
            return CompletableFuture.completedFuture(stored);
        }
    }

    @Test
    void onlyApprovedEntriesCanEnqueueFinanceLiability() {
        FakeOutbox outbox = new FakeOutbox();
        CommissionEntry pending = entry(CommissionStatus.PENDING);
        CommissionEntryRepository entries = new CommissionEntryRepository() {
            @Override public CompletionStage<CommissionEntry> saveOrGet(CommissionEntry proposed) {
                return CompletableFuture.completedFuture(proposed);
            }
            @Override public CompletionStage<Optional<CommissionEntry>> findById(String tenant, UUID id) {
                return CompletableFuture.completedFuture(Optional.of(pending));
            }
        };
        DefaultCommissionFinanceService service = new DefaultCommissionFinanceService(
                entries, outbox, financePort(new AtomicInteger(), "unused"));
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> service.requestLiabilityPosting(context, ENTRY_ID).toCompletableFuture().join());
        assertEquals(0, outbox.enqueueCalls.get());
    }

    @Test
    void enqueuesApprovedLiabilityAndDispatchesWithStableIdempotencyKey() {
        FakeOutbox outbox = new FakeOutbox();
        CommissionEntry approved = entry(CommissionStatus.APPROVED);
        CommissionEntryRepository entries = new CommissionEntryRepository() {
            @Override public CompletionStage<CommissionEntry> saveOrGet(CommissionEntry proposed) {
                return CompletableFuture.completedFuture(proposed);
            }
            @Override public CompletionStage<Optional<CommissionEntry>> findById(String tenant, UUID id) {
                return CompletableFuture.completedFuture(tenant.equals(TENANT) && id.equals(ENTRY_ID)
                        ? Optional.of(approved) : Optional.empty());
            }
        };
        AtomicInteger financeCalls = new AtomicInteger();
        DefaultCommissionFinanceService service = new DefaultCommissionFinanceService(
                entries, outbox, financePort(financeCalls, "JE-42"));

        CommissionFinancePosting request = service.requestLiabilityPosting(context, ENTRY_ID)
                .toCompletableFuture().join();
        assertEquals("affiliate-finance:" + TENANT + ":" + ENTRY_ID, request.idempotencyKey());
        assertEquals(CommissionFinancePostingStatus.PENDING, request.status());

        int dispatched = service.dispatchPending(context, "worker-a", 10).toCompletableFuture().join();
        assertEquals(1, dispatched);
        assertEquals(1, financeCalls.get());
        assertEquals(CommissionFinancePostingStatus.POSTED, outbox.completed.status());
        assertEquals("JE-42", outbox.completed.externalReference());
    }

    @Test
    void reconcilesAmbiguousFinanceResultWithoutPostingTwice() {
        FakeOutbox outbox = new FakeOutbox();
        CommissionEntry approved = entry(CommissionStatus.APPROVED);
        CommissionEntryRepository entries = new CommissionEntryRepository() {
            @Override public CompletionStage<CommissionEntry> saveOrGet(CommissionEntry proposed) {
                return CompletableFuture.completedFuture(proposed);
            }
            @Override public CompletionStage<Optional<CommissionEntry>> findById(String tenant, UUID id) {
                return CompletableFuture.completedFuture(Optional.of(approved));
            }
        };
        CommissionFinancePort finance = new CommissionFinancePort() {
            @Override public CompletionStage<FinancePostingReceipt> postCommissionLiability(CommissionFinancePosting p) {
                return CompletableFuture.failedFuture(new RuntimeException("timeout"));
            }
            @Override public CompletionStage<Optional<FinancePostingReceipt>> findPosting(String tenant, String key) {
                return CompletableFuture.completedFuture(Optional.of(new FinancePostingReceipt(
                        tenant, key, "JE-43", "USD", new BigDecimal("10.00"), NOW.plusSeconds(1))));
            }
        };
        DefaultCommissionFinanceService service = new DefaultCommissionFinanceService(entries, outbox, finance);
        CommissionFinancePosting request = service.requestLiabilityPosting(context, ENTRY_ID)
                .toCompletableFuture().join();
        outbox.stored = request.reconciliationRequired("timeout", NOW.plusSeconds(1));

        assertTrue(service.reconcile(context, request.id()).toCompletableFuture().join());
        assertEquals(CommissionFinancePostingStatus.POSTED, outbox.stored.status());
        assertEquals("JE-43", outbox.stored.externalReference());
    }

    private CommissionFinancePort financePort(AtomicInteger calls, String reference) {
        return new CommissionFinancePort() {
            @Override public CompletionStage<FinancePostingReceipt> postCommissionLiability(CommissionFinancePosting p) {
                calls.incrementAndGet();
                return CompletableFuture.completedFuture(new FinancePostingReceipt(
                        p.tenantId(), p.idempotencyKey(), reference, p.currency(), p.amount(), NOW.plusSeconds(1)));
            }
            @Override public CompletionStage<Optional<FinancePostingReceipt>> findPosting(String tenant, String key) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
        };
    }
}
