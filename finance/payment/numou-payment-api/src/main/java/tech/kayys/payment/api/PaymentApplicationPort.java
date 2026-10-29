package tech.kayys.payment.api;

import java.util.Optional;

/**
 * Consumer-facing application contract for payment execution.
 *
 * <p>Implementations must enforce tenant authorization, durable idempotency,
 * concurrency-safe state transitions, and verified provider outcomes. This
 * interface deliberately exposes neither persistence entities nor gateway
 * provider types. Remote adapters may map the same commands and results to a
 * versioned transport contract.</p>
 *
 * <p>Implementations must not treat a timeout as proof that a provider
 * operation failed; callers should query the operation and rely on the
 * implementation's reconciliation behavior.</p>
 */
public interface PaymentApplicationPort {

    /**
     * Creates a payment obligation or returns the existing operation for an
     * identical tenant-scoped idempotency key and request.
     *
     * @throws IdempotencyConflictException if the key was previously used for
     *         a semantically different request
     * @throws PaymentOperationRejectedException if the command is invalid or
     *         cannot be accepted under the tenant's payment policy
     */
    PaymentObligation createOrGet(CreatePaymentObligation command);

    /**
     * Retrieves an obligation within the supplied tenant's authorization
     * scope. An identifier alone is not authorization.
     */
    Optional<PaymentObligation> find(String tenantId, String paymentId);

    /**
     * Requests a full or partial refund. The returned status describes the
     * refund operation, not necessarily final provider settlement.
     */
    RefundOperation requestRefund(RequestPaymentRefund command);

    /** Idempotency key reused for a different semantic command. */
    final class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException(String message) {
            super(message);
        }
    }

    /** Command rejected by payment policy or validation. */
    final class PaymentOperationRejectedException extends RuntimeException {
        public PaymentOperationRejectedException(String message) {
            super(message);
        }
    }
}