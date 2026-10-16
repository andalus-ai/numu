package tech.kayys.syirkah.accounting.domain.event;

import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Raised each period when deferred Murabahah profit is recognised into income.
 * Supports PSAK 102 straight-line and AAOIFI FAS 2 effective-profit-rate methods.
 */
public record ProfitRecognized(
        UUID eventId,
        Instant occurredAt,
        TenantRef tenantId,
        LedgerId ledgerId,
        String correlationId,
        String causationId,
        String contractId,
        int periodNumber,
        BigDecimal recognizedAmount,
        BigDecimal remainingDeferred
) implements AccountingEvent {

    /** Convenience factory. */
    public static ProfitRecognized of(
            TenantRef tenantId,
            LedgerId ledgerId,
            String contractId,
            int periodNumber,
            BigDecimal recognizedAmount,
            BigDecimal remainingDeferred,
            String correlationId,
            String causationId) {
        return new ProfitRecognized(
                UUID.randomUUID(), Instant.now(),
                tenantId, ledgerId, correlationId, causationId,
                contractId, periodNumber, recognizedAmount, remainingDeferred);
    }
}
