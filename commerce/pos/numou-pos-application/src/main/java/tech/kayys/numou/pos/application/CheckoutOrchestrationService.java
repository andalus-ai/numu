package tech.kayys.numou.pos.application;

import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.payment.api.*;
import tech.kayys.numou.pos.application.port.out.CheckoutInventoryPort;
import tech.kayys.numou.pos.application.port.out.CheckoutRepository;
import tech.kayys.numou.pos.domain.CheckoutSession;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Retryable checkout saga. UNKNOWN/PENDING payments never release stock.
 * The inventory adapter must provide durable idempotency and atomic reservation consumption.
 */
public final class CheckoutOrchestrationService {
    private final CheckoutRepository repository;
    private final CheckoutInventoryPort inventory;
    private final PaymentApplicationPort payments;
    private final Clock clock;

    public CheckoutOrchestrationService(CheckoutRepository repository, CheckoutInventoryPort inventory,
            PaymentApplicationPort payments, Clock clock) {
        this.repository = Objects.requireNonNull(repository); this.inventory = Objects.requireNonNull(inventory);
        this.payments = Objects.requireNonNull(payments); this.clock = Objects.requireNonNull(clock);
    }

    /** Starts or resumes checkout using an already-authorized total snapshot. */
    public CheckoutSession start(String tenantId, UUID checkoutId, UUID saleId, Money total) {
        String tenant = required(tenantId); Objects.requireNonNull(checkoutId); Objects.requireNonNull(saleId); Objects.requireNonNull(total);
        CheckoutSession s = repository.find(tenant, checkoutId)
                .orElseGet(() -> repository.create(CheckoutSession.start(checkoutId, tenant, saleId, total, clock.instant())));
        if (!s.saleId().equals(saleId) || !s.amount().equals(total)) throw new IllegalStateException("checkout key reused with different sale/amount");
        if (s.status() == CheckoutSession.Status.STOCK_PENDING) {
            var reservation = inventory.reserve(tenant, checkoutId, saleId, key(checkoutId, "reserve"));
            s = repository.update(s.reserveStock(reservation.reference(), clock.instant()), s.version());
        }
        if (s.status() == CheckoutSession.Status.PAYMENT_PENDING && s.paymentId() == null) {
            PaymentObligation p = payments.createOrGet(new CreatePaymentObligation(tenant, key(checkoutId, "payment"),
                    checkoutId.toString(), total.amount(), total.currency().code(), "POS checkout " + checkoutId,
                    Map.of("saleId", saleId.toString(), "checkoutId", checkoutId.toString())));
            s = repository.update(s.attachPayment(p.paymentId(), clock.instant()), s.version());
        }
        return s;
    }

    /** Poll/reconcile after provider callbacks or worker retries; never infer failure from timeout. */
    public CheckoutSession reconcile(String tenantId, UUID checkoutId) {
        String tenant = required(tenantId);
        CheckoutSession s = repository.find(tenant, Objects.requireNonNull(checkoutId))
                .orElseThrow(() -> new IllegalArgumentException("checkout not found"));
        if (s.status() == CheckoutSession.Status.COMPLETED || s.status() == CheckoutSession.Status.CANCELLED
                || s.status() == CheckoutSession.Status.STOCK_PENDING) return s;
        PaymentObligation p = payments.find(tenant, s.paymentId())
                .orElseThrow(() -> new IllegalStateException("payment obligation missing; manual reconciliation required"));
        if (!p.tenantId().equals(tenant) || !p.paymentId().equals(s.paymentId())
                || p.amount().compareTo(s.amount().amount()) != 0 || !p.currency().equals(s.amount().currency().code()))
            throw new IllegalStateException("payment obligation does not match checkout");
        if (p.status() == PaymentStatus.SUCCEEDED) {
            if (s.status() != CheckoutSession.Status.PAID) s = repository.update(s.markPaid(clock.instant()), s.version());
            inventory.commit(tenant, s.reservationReference(), key(checkoutId, "commit-stock"));
            return repository.update(s.complete(clock.instant()), s.version());
        }
        if (p.status() == PaymentStatus.FAILED || p.status() == PaymentStatus.CANCELLED || p.status() == PaymentStatus.EXPIRED) {
            inventory.release(tenant, s.reservationReference(), key(checkoutId, "release-stock"));
            return repository.update(s.cancel(clock.instant()), s.version());
        }
        if (p.status() == PaymentStatus.UNKNOWN && s.status() != CheckoutSession.Status.PAYMENT_UNKNOWN)
            return repository.update(s.markUnknown(clock.instant()), s.version());
        return s;
    }

    private static String key(UUID id, String operation) { return "pos:" + id + ":" + operation; }
    private static String required(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("tenantId cannot be blank"); return value.trim(); }
}
