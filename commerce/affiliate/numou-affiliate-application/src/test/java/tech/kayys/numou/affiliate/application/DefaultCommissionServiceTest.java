package tech.kayys.numou.affiliate.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.affiliate.domain.*;
import tech.kayys.numou.affiliate.spi.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DefaultCommissionServiceTest {
    private static final String TENANT = "tenant-a";
    private static final UUID PROGRAM = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PARTNER = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");

    @Test
    void calculatesFromSourceVerifiedConversionAndPersistsThroughAtomicBoundary() {
        AttributionDecision attribution = new AttributionDecision(UUID.randomUUID(), TENANT, PROGRAM,
                "commerce.order", "order-1", "paid-1", AttributionOutcome.ATTRIBUTED,
                PARTNER, UUID.randomUUID(), UUID.randomUUID(), 1, NOW, NOW.plusSeconds(1));
        CommissionConversion conversion = new CommissionConversion(TENANT, "commerce.order", "order-1",
                "paid-1", attribution, new BigDecimal("250.00"), "USD", NOW.plusSeconds(2));
        CommissionPlan plan = new CommissionPlan(UUID.randomUUID(), TENANT, PROGRAM, 1,
                CommissionType.PERCENTAGE, "USD", new BigDecimal("12.5"), NOW, NOW);
        AtomicInteger saves = new AtomicInteger();
        CommissionEntryRepository entries = new CommissionEntryRepository() {
            @Override public java.util.concurrent.CompletionStage<CommissionEntry> saveOrGet(CommissionEntry proposed) {
                saves.incrementAndGet();
                return CompletableFuture.completedFuture(proposed);
            }
            @Override public java.util.concurrent.CompletionStage<Optional<CommissionEntry>> findById(String tenant, UUID id) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
        };
        DefaultCommissionService service = new DefaultCommissionService(
                (tenant, type, source, event) -> CompletableFuture.completedFuture(Optional.of(conversion)),
                (tenant, programId, at) -> CompletableFuture.completedFuture(Optional.of(plan)),
                entries);

        CommissionEntry result = service.calculateCommission(
                new AffiliateCommandContext(TENANT, "operator-1", "request-1", "corr-1"),
                PROGRAM, "commerce.order", "order-1", "paid-1").toCompletableFuture().join();

        assertEquals(new BigDecimal("31.25"), result.commissionAmount());
        assertEquals(CommissionStatus.PENDING, result.status());
        assertEquals(1, saves.get());
    }

    @Test
    void refusesMissingAuthoritativeConversionAndDoesNotPersist() {
        AtomicInteger saves = new AtomicInteger();
        CommissionEntryRepository entries = new CommissionEntryRepository() {
            @Override public java.util.concurrent.CompletionStage<CommissionEntry> saveOrGet(CommissionEntry proposed) {
                saves.incrementAndGet();
                return CompletableFuture.completedFuture(proposed);
            }
            @Override public java.util.concurrent.CompletionStage<Optional<CommissionEntry>> findById(String tenant, UUID id) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
        };
        DefaultCommissionService service = new DefaultCommissionService(
                (tenant, type, source, event) -> CompletableFuture.completedFuture(Optional.empty()),
                (tenant, programId, at) -> CompletableFuture.completedFuture(Optional.empty()),
                entries);
        assertThrows(java.util.concurrent.CompletionException.class, () -> service.calculateCommission(
                new AffiliateCommandContext(TENANT, "operator-1", "request-1", "corr-1"),
                PROGRAM, "commerce.order", "order-1", "paid-1").toCompletableFuture().join());
        assertEquals(0, saves.get());
    }
}
