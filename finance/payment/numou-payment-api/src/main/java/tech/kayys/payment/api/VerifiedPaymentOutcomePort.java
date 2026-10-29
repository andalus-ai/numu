package tech.kayys.payment.api;

/**
 * Provider-side application contract for applying an outcome only after a
 * trusted provider adapter has authenticated the callback and resolved the
 * provider account.
 *
 * <p>This contract is deliberately separate from {@link PaymentApplicationPort}:
 * business consumers may create and query payment obligations or request
 * refunds, but should not be granted a callback/state-mutation dependency.
 * Composition roots should expose this port only to trusted provider adapters
 * and callback-processing workers.</p>
 *
 * <p>Implementations must validate tenant ownership, payment reference,
 * amount, currency, provider reference, allowed state transitions, and event
 * idempotency. Browser redirects and unverified callback payloads are not
 * valid inputs. This Java interface is an architectural boundary, not a
 * substitute for authentication and authorization at runtime boundaries.</p>
 */
public interface VerifiedPaymentOutcomePort {

    /**
     * Applies a verified provider event transactionally and idempotently.
     *
     * @throws PaymentApplicationPort.IdempotencyConflictException if the
     *         provider reuses an event identifier for a different outcome
     * @throws PaymentApplicationPort.PaymentOperationRejectedException if the
     *         outcome conflicts with the tenant's payment obligation or state
     */
    PaymentObligation applyVerifiedOutcome(VerifiedPaymentOutcome outcome);
}
