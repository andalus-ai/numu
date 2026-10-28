package tech.kayys.numou.commerce.marketplace.application.checkout;

import java.util.List;
import java.util.Objects;

/**
 * Tenant-scoped batch entry point for a scheduler or operator-triggered recovery job.
 * Scheduling, authentication, retry cadence, and alert delivery belong to the host composition.
 */
public final class MarketplaceCheckoutRecoveryService {
    private final MarketplaceCheckoutRepository checkouts;
    private final MarketplaceCheckoutService checkoutService;

    public MarketplaceCheckoutRecoveryService(MarketplaceCheckoutRepository checkouts,
                                              MarketplaceCheckoutService checkoutService) {
        this.checkouts = Objects.requireNonNull(checkouts, "checkouts");
        this.checkoutService = Objects.requireNonNull(checkoutService, "checkoutService");
    }

    /**
     * Attempts recovery for the oldest recoverable checkouts in exactly one tenant.
     * A failure on one checkout does not prevent the rest of the bounded batch from running.
     */
    public RecoveryReport recoverTenant(String tenantId, int limit) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be between 1 and 1000");

        List<MarketplaceCheckout> candidates = checkouts.findRecoverable(tenantId, limit);
        int ready = 0;
        int failed = 0;
        int compensationRequired = 0;
        int reconciliationRequired = 0;
        int stillAwaitingPayment = 0;
        int errors = 0;

        for (MarketplaceCheckout candidate : candidates) {
            try {
                MarketplaceCheckout result = checkoutService.recoverCheckout(tenantId, candidate.id());
                switch (result.status()) {
                    case READY_FOR_FULFILLMENT -> ready++;
                    case FAILED -> failed++;
                    case COMPENSATION_REQUIRED -> compensationRequired++;
                    case RECONCILIATION_REQUIRED -> reconciliationRequired++;
                    case AWAITING_PAYMENT -> stillAwaitingPayment++;
                    case RESERVING_INVENTORY -> reconciliationRequired++;
                }
            } catch (RuntimeException failure) {
                // Keep the batch moving; the durable row remains eligible for the next attempt.
                errors++;
            }
        }
        return new RecoveryReport(candidates.size(), ready, failed, compensationRequired,
                reconciliationRequired, stillAwaitingPayment, errors);
    }

    public record RecoveryReport(int attempted, int readyForFulfillment, int failed,
                                 int compensationRequired, int reconciliationRequired,
                                 int awaitingPayment, int errors) { }
}
