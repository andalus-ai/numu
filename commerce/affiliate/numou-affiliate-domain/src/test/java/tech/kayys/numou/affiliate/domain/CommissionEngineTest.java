package tech.kayys.numou.affiliate.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CommissionEngineTest {
    private static final String TENANT = "tenant-a";
    private static final UUID PROGRAM = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PARTNER = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");

    private AttributionDecision attribution() {
        return new AttributionDecision(UUID.randomUUID(), TENANT, PROGRAM, "commerce.order",
                "order-42", "paid-event-42", AttributionOutcome.ATTRIBUTED, PARTNER,
                UUID.randomUUID(), UUID.randomUUID(), 1, NOW, NOW.plusSeconds(1));
    }

    private CommissionConversion conversion(String currency, String amount) {
        return new CommissionConversion(TENANT, "commerce.order", "order-42", "paid-event-42",
                attribution(), new BigDecimal(amount), currency, NOW.plusSeconds(2));
    }

    private CommissionPlan plan(CommissionType type, String currency, String value) {
        return new CommissionPlan(UUID.randomUUID(), TENANT, PROGRAM, 3, type,
                currency, new BigDecimal(value), NOW, NOW);
    }

    @Test
    void calculatesPercentageAndRoundsUsingCurrencyFractionDigits() {
        CommissionEntry result = CommissionEngine.calculate(UUID.randomUUID(),
                plan(CommissionType.PERCENTAGE, "IDR", "7.5"),
                conversion("IDR", "123456.78"), NOW.plusSeconds(3));

        assertEquals(new BigDecimal("9259.26"), result.commissionAmount());
        assertEquals("IDR", result.currency());
        assertEquals(CommissionStatus.PENDING, result.status());
        assertEquals(CommissionEntryType.EARNING, result.type());
        assertTrue(result.idempotencyKey().contains("paid-event-42"));
    }

    @Test
    void calculatesFixedCommissionAndKeepsSnapshotTiedToPlanVersion() {
        CommissionPlan plan = plan(CommissionType.FIXED, "USD", "4.50");
        CommissionEntry result = CommissionEngine.calculate(UUID.randomUUID(),
                plan, conversion("USD", "100.00"), NOW.plusSeconds(3));

        assertEquals(new BigDecimal("4.50"), result.commissionAmount());
        assertEquals(plan.id(), result.planId());
        assertEquals(3, result.planVersion());
    }

    @Test
    void rejectsCurrencyMismatchAndPlanNotEffectiveAtConversionTime() {
        assertThrows(IllegalArgumentException.class, () -> CommissionEngine.calculate(UUID.randomUUID(),
                plan(CommissionType.FIXED, "USD", "2.00"), conversion("IDR", "100"), NOW));
        CommissionPlan future = new CommissionPlan(UUID.randomUUID(), TENANT, PROGRAM, 4,
                CommissionType.PERCENTAGE, "IDR", new BigDecimal("10"),
                NOW.plusSeconds(100), NOW);
        assertThrows(IllegalArgumentException.class, () -> CommissionEngine.calculate(UUID.randomUUID(),
                future, conversion("IDR", "100"), NOW.plusSeconds(4)));
    }

    @Test
    void reversalIsASeparateLinkedNegativeEntryAndReplayKeyIsStable() {
        CommissionEntry original = CommissionEngine.calculate(UUID.randomUUID(),
                plan(CommissionType.PERCENTAGE, "USD", "10"),
                conversion("USD", "100"), NOW.plusSeconds(3));
        CommissionEntry reversal = CommissionEngine.reverse(UUID.randomUUID(), original,
                "refund-event-7", NOW.plusSeconds(4));

        assertEquals(CommissionEntryType.REVERSAL, reversal.type());
        assertEquals(original.id(), reversal.originalEntryId());
        assertEquals(new BigDecimal("-10.00"), reversal.commissionAmount());
        assertEquals("affiliate-reversal:" + original.id() + ":refund-event-7", reversal.idempotencyKey());
        assertThrows(IllegalStateException.class, () -> CommissionEngine.reverse(UUID.randomUUID(),
                original.transitionTo(CommissionStatus.REVERSED), "refund-event-8", NOW.plusSeconds(5)));
    }

    @Test
    void enforcesCommissionLifecycleTransitions() {
        CommissionEntry pending = CommissionEngine.calculate(UUID.randomUUID(),
                plan(CommissionType.FIXED, "USD", "5"),
                conversion("USD", "100"), NOW.plusSeconds(3));
        assertEquals(CommissionStatus.APPROVED, pending.transitionTo(CommissionStatus.APPROVED).status());
        assertEquals(CommissionStatus.HELD, pending.transitionTo(CommissionStatus.HELD).status());
        assertThrows(IllegalStateException.class, () -> pending.transitionTo(CommissionStatus.REVERSED)
                .transitionTo(CommissionStatus.APPROVED));
    }
}
