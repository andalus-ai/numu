package tech.kayys.numou.affiliate.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.affiliate.domain.*;
import tech.kayys.numou.affiliate.spi.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DefaultAttributionServiceTest {
    private static final String TENANT = "tenant-a";
    private static final UUID PROGRAM = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PARTNER = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant CONVERSION_AT = Instant.now().minusSeconds(60);

    private AffiliateCommandContext context() {
        return new AffiliateCommandContext(TENANT, "operator-1", "request-123", "corr-123");
    }

    @Test
    void qualifiesAndPersistsOneDeterministicAttributionDecision() {
        AttributionPolicy policy = new AttributionPolicy(UUID.randomUUID(), TENANT, PROGRAM, 2,
                AttributionModel.LAST_TOUCH, 3600, CONVERSION_AT.minusSeconds(3600), CONVERSION_AT.minusSeconds(3600));
        AffiliateTouchpoint touch = new AffiliateTouchpoint(UUID.randomUUID(), TENANT, PROGRAM, PARTNER,
                UUID.randomUUID(), "subject-key", CONVERSION_AT.minusSeconds(20));
        ConversionReference conversion = new ConversionReference(
                "marketplace.order", "order-9", "event-9", "subject-key", CONVERSION_AT);
        AtomicInteger saves = new AtomicInteger();
        AttributionDecisionRepository decisionRepo = new AttributionDecisionRepository() {
            @Override
            public java.util.concurrent.CompletionStage<AttributionDecision> saveOrGet(AttributionDecision proposed) {
                saves.incrementAndGet();
                return CompletableFuture.completedFuture(proposed);
            }
            @Override
            public java.util.concurrent.CompletionStage<Optional<AttributionDecision>> findBySourceEvent(
                    String tenantId, String sourceType, String sourceId, String eventId) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
        };
        DefaultAttributionService service = new DefaultAttributionService(
                (tenant, type, sourceId, eventId) -> CompletableFuture.completedFuture(Optional.of(conversion)),
                (tenant, programId, at) -> CompletableFuture.completedFuture(Optional.of(policy)),
                (tenant, programId, subject, from, to) -> CompletableFuture.completedFuture(List.of(touch)),
                decisionRepo);

        AttributionDecision result = service.attributeConversion(
                context(), PROGRAM, "marketplace.order", "order-9", "event-9").toCompletableFuture().join();

        assertEquals(AttributionOutcome.ATTRIBUTED, result.outcome());
        assertEquals(PARTNER, result.partnerId());
        assertEquals(touch.id(), result.touchpointId());
        assertEquals(2, result.policyVersion());
        assertEquals(1, saves.get());
    }

    @Test
    void exactReplayReturnsStoredDecisionWithoutRecheckingSource() {
        Instant now = Instant.now();
        AttributionDecision stored = new AttributionDecision(UUID.randomUUID(), TENANT, PROGRAM,
                "marketplace.order", "order-9", "event-9", AttributionOutcome.UNATTRIBUTED,
                null, null, UUID.randomUUID(), 1, now.minusSeconds(10), now);
        AtomicInteger sourceCalls = new AtomicInteger();
        AttributionDecisionRepository decisionRepo = new AttributionDecisionRepository() {
            @Override
            public java.util.concurrent.CompletionStage<AttributionDecision> saveOrGet(AttributionDecision proposed) {
                return CompletableFuture.completedFuture(proposed);
            }
            @Override
            public java.util.concurrent.CompletionStage<Optional<AttributionDecision>> findBySourceEvent(
                    String tenantId, String sourceType, String sourceId, String eventId) {
                assertEquals(TENANT, tenantId);
                return CompletableFuture.completedFuture(Optional.of(stored));
            }
        };
        DefaultAttributionService service = new DefaultAttributionService(
                (tenant, type, sourceId, eventId) -> {
                    sourceCalls.incrementAndGet();
                    return CompletableFuture.completedFuture(Optional.empty());
                },
                (tenant, programId, at) -> CompletableFuture.completedFuture(Optional.empty()),
                (tenant, programId, subject, from, to) -> CompletableFuture.completedFuture(List.of()),
                decisionRepo);

        AttributionDecision result = service.attributeConversion(
                context(), PROGRAM, "marketplace.order", "order-9", "event-9").toCompletableFuture().join();

        assertSame(stored, result);
        assertEquals(0, sourceCalls.get());
    }

    @Test
    void unverifiedConversionCannotCreateAttribution() {
        AtomicInteger saves = new AtomicInteger();
        AttributionDecisionRepository decisionRepo = new AttributionDecisionRepository() {
            @Override
            public java.util.concurrent.CompletionStage<AttributionDecision> saveOrGet(AttributionDecision proposed) {
                saves.incrementAndGet();
                return CompletableFuture.completedFuture(proposed);
            }
            @Override
            public java.util.concurrent.CompletionStage<Optional<AttributionDecision>> findBySourceEvent(
                    String tenantId, String sourceType, String sourceId, String eventId) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
        };
        DefaultAttributionService service = new DefaultAttributionService(
                (tenant, type, sourceId, eventId) -> CompletableFuture.completedFuture(Optional.empty()),
                (tenant, programId, at) -> CompletableFuture.completedFuture(Optional.empty()),
                (tenant, programId, subject, from, to) -> CompletableFuture.completedFuture(List.of()),
                decisionRepo);

        assertThrows(java.util.concurrent.CompletionException.class, () -> service.attributeConversion(
                context(), PROGRAM, "marketplace.order", "order-9", "event-9").toCompletableFuture().join());
        assertEquals(0, saves.get());
    }
}
