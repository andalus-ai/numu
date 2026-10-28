package tech.kayys.numou.affiliate.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SettlementBatchTest {
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");

    private SettlementBatch batch() {
        return new SettlementBatch(UUID.randomUUID(), "tenant-a", "IDR",
                List.of(
                        new SettlementBatchItem(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10000"), "IDR"),
                        new SettlementBatchItem(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("25000"), "IDR")),
                new BigDecimal("35000"), "settlement-key-1", SettlementBatchStatus.READY_FOR_APPROVAL,
                "maker", null, NOW, NOW, 0);
    }

    @Test
    void makerCheckerAndLifecycleAreEnforced() {
        SettlementBatch draft = batch();
        assertThrows(IllegalArgumentException.class, () -> draft.approve("maker", NOW.plusSeconds(1)));

        SettlementBatch approved = draft.approve("checker", NOW.plusSeconds(1));
        assertEquals(SettlementBatchStatus.APPROVED, approved.status());
        assertEquals(1, approved.version());
        SettlementBatch submitting = approved.beginSubmission(NOW.plusSeconds(2));
        assertEquals(SettlementBatchStatus.SUBMITTING, submitting.status());
        SettlementBatch completed = submitting.completed(NOW.plusSeconds(3));
        assertEquals(SettlementBatchStatus.COMPLETED, completed.status());
        assertThrows(IllegalStateException.class, () -> completed.beginSubmission(NOW.plusSeconds(4)));
    }

    @Test
    void batchMustBeSingleCurrencyWithExactTotalAndUniquePayouts() {
        UUID payoutId = UUID.randomUUID();
        UUID partnerId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new SettlementBatch(UUID.randomUUID(), "tenant-a", "IDR",
                List.of(new SettlementBatchItem(payoutId, partnerId, new BigDecimal("100"), "IDR"),
                        new SettlementBatchItem(payoutId, partnerId, new BigDecimal("200"), "IDR")),
                new BigDecimal("300"), "key", SettlementBatchStatus.READY_FOR_APPROVAL, "maker", null, NOW, NOW, 0));

        assertThrows(IllegalArgumentException.class, () -> new SettlementBatch(UUID.randomUUID(), "tenant-a", "IDR",
                List.of(new SettlementBatchItem(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100"), "IDR"),
                        new SettlementBatchItem(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("200"), "USD")),
                new BigDecimal("300"), "key", SettlementBatchStatus.READY_FOR_APPROVAL, "maker", null, NOW, NOW, 0));

        assertThrows(IllegalArgumentException.class, () -> new SettlementBatch(UUID.randomUUID(), "tenant-a", "IDR",
                List.of(new SettlementBatchItem(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100"), "IDR")),
                new BigDecimal("101"), "key", SettlementBatchStatus.READY_FOR_APPROVAL, "maker", null, NOW, NOW, 0));
    }

    @Test
    void completedBatchRequiresApprovalIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new SettlementBatch(UUID.randomUUID(), "tenant-a", "IDR",
                List.of(new SettlementBatchItem(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, "IDR")),
                BigDecimal.ONE, "key", SettlementBatchStatus.COMPLETED, "maker", null, NOW, NOW, 0));
    }
}
