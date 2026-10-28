package tech.kayys.numou.affiliate.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PartnerPayoutTest {
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");

    private PartnerPayout payout() {
        return new PartnerPayout(UUID.randomUUID(), "tenant-a", UUID.randomUUID(), UUID.randomUUID(),
                "destination-1", "IDR", new BigDecimal("125000"), "payout-key-1", "correlation-1",
                PartnerPayoutStatus.REQUESTED, 0, null, null, NOW, NOW);
    }

    @Test
    void payoutIsNotPaidUntilProviderConfirmsIt() {
        PartnerPayout payout = payout().submitted("provider-1", NOW.plusSeconds(1));

        assertEquals(PartnerPayoutStatus.SUBMITTED, payout.status());
        assertThrows(IllegalStateException.class, () -> payout.held("hold", NOW.plusSeconds(2))
                .paid("provider-1", NOW.plusSeconds(3)));
        PartnerPayout paid = payout.paid("provider-1", NOW.plusSeconds(2));
        assertEquals(PartnerPayoutStatus.PAID, paid.status());
        assertEquals("provider-1", paid.providerReference());
    }

    @Test
    void ambiguousPayoutCanBeReconciledButCannotBeSilentlyRetried() {
        PartnerPayout ambiguous = payout().reconciliationRequired("provider timeout", NOW.plusSeconds(1));

        assertEquals(PartnerPayoutStatus.RECONCILIATION_REQUIRED, ambiguous.status());
        assertThrows(IllegalStateException.class,
                () -> ambiguous.submitted("provider-2", NOW.plusSeconds(2)));
        PartnerPayout paid = ambiguous.paid("provider-1", NOW.plusSeconds(3));
        assertEquals(PartnerPayoutStatus.PAID, paid.status());
    }

    @Test
    void payoutRequiresPositiveAmountAndPaidProviderReference() {
        assertThrows(IllegalArgumentException.class, () -> new PartnerPayout(UUID.randomUUID(), "tenant-a",
                UUID.randomUUID(), UUID.randomUUID(), "destination-1", "IDR", BigDecimal.ZERO,
                "payout-key", "correlation", PartnerPayoutStatus.REQUESTED, 0, null, null, NOW, NOW));
        assertThrows(IllegalArgumentException.class, () -> new PartnerPayout(UUID.randomUUID(), "tenant-a",
                UUID.randomUUID(), UUID.randomUUID(), "destination-1", "IDR", BigDecimal.ONE,
                "payout-key", "correlation", PartnerPayoutStatus.PAID, 1, null, null, NOW, NOW));
    }
}
