package tech.kayys.syirkah.accounting.domain.event;

import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a Murabahah (cost-plus) financing contract is initiated.
 * Carries cost, margin and tenor used for PSAK 102 / AAOIFI FAS 2 profit recognition.
 */
public record MurabahaCreated(
        UUID eventId,
        Instant occurredAt,
        TenantRef tenantId,
        LedgerId ledgerId,
        String correlationId,
        String causationId,
        String contractId,
        BigDecimal cost,
        BigDecimal margin,
        BigDecimal sellingPrice,
        int tenorMonths
) implements AccountingEvent {

    /** Convenience factory. */
    public static MurabahaCreated of(
            TenantRef tenantId,
            LedgerId ledgerId,
            String contractId,
            BigDecimal cost,
            BigDecimal margin,
            BigDecimal sellingPrice,
            int tenorMonths,
            String correlationId,
            String causationId) {
        return new MurabahaCreated(
                UUID.randomUUID(), Instant.now(),
                tenantId, ledgerId, correlationId, causationId,
                contractId, cost, margin, sellingPrice, tenorMonths);
    }
}
