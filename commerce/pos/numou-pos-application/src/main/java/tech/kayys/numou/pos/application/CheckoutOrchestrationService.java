package tech.kayys.numou.pos.application;

import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.pos.application.port.out.CheckoutInventoryPort;
import tech.kayys.numou.pos.application.port.out.CheckoutPaymentPort;
import tech.kayys.numou.pos.application.port.out.CheckoutPaymentPort.PaymentRequest;
import tech.kayys.numou.pos.application.port.out.CheckoutPaymentPort.PaymentSnapshot;
import tech.kayys.numou.pos.application.port.out.CheckoutPaymentPort.Status;
import tech.kayys.numou.pos.application.port.out.CheckoutPricingPort;
import tech.kayys.numou.pos.application.port.out.CheckoutRepository;
import tech.kayys.numou.pos.application.port.out.CheckoutSalePort;
import tech.kayys.numou.pos.domain.CheckoutSession;
import tech.kayys.numou.pos.domain.Sale;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Retryable checkout saga. The sale and quote ports are the authority for
 * checkout eligibility and amount; callers cannot supply a payable total.
 * UNKNOWN/PENDING payments never release stock.
 */
public final class CheckoutOrchestrationService {
    private final CheckoutRepository repository;
    private final CheckoutSalePort sales;
    private final CheckoutPricingPort pricing;
    private final CheckoutInventoryPort inventory;
    private final CheckoutPaymentPort payments;
    private final Clock clock;

    public CheckoutOrchestrationService(CheckoutRepository repository, CheckoutSalePort sales,
            CheckoutPricingPort pricing, CheckoutInventoryPort inventory,
            CheckoutPaymentPort payments, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.sales = Objects.requireNonNull(sales);
        this.pricing = Objects.requireNonNull(pricing);
        this.inventory = Objects.requireNonNull(inventory);
        this.payments = Objects.requireNonNull(payments);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Starts or resumes checkout using a persisted sale and a trusted quote. */
    public CheckoutSession start(String tenantId, UUID checkoutId, UUID saleId) {
        String tenant = required(tenantId);
        Objects.requireNonNull(checkoutId, "checkoutId");
        Objects.requireNonNull(saleId, "saleId");

        CheckoutSession s = repository.find(tenant, checkoutId).orElse(null);
        if (s == null) {
            UUID tenantUuid;
            try {
                tenantUuid = UUID.fromString(tenant);
            } catch (IllegalArgumentException invalidTenant) {
                throw new IllegalArgumentException("POS checkout tenantId must be a UUID", invalidTenant);
            }
            Sale sale = sales.find(tenant, saleId)
                    .orElseThrow(() -> new IllegalArgumentException("sale not found"));
            if (!sale.id().equals(saleId) || !sale.tenantId().equals(tenantUuid)) {
                throw new IllegalStateException("sale does not belong to the requested tenant");
            }
            if (sale.status() != Sale.Status.OPEN) {
                throw new IllegalStateException("only an open sale can enter checkout");
            }
            if (sale.lines().isEmpty()) {
                throw new IllegalStateException("sale must contain at least one line before checkout");
            }
            Money total = Objects.requireNonNull(pricing.quote(tenant, sale), "checkout quote cannot be null");
            if (!total.isPositive() || !total.currency().code().equals(sale.currency())) {
                throw new IllegalStateException("checkout quote must be positive and use the sale currency");
            }
            s = repository.create(CheckoutSession.start(checkoutId, tenant, saleId, total, clock.instant()));
        } else if (!s.saleId().equals(saleId)) {
            throw new IllegalStateException("checkout key reused with a different sale");
        }

        if (s.status() == CheckoutSession.Status.STOCK_PENDING) {
            var reservation = inventory.reserve(tenant, checkoutId, saleId, key(checkoutId, "reserve"));
            s = repository.update(s.reserveStock(reservation.reference(), clock.instant()), s.version());
        }
        if (s.status() == CheckoutSession.Status.PAYMENT_PENDING && s.paymentId() == null) {
            PaymentSnapshot p = payments.createOrGet(new PaymentRequest(tenant, key(checkoutId, "payment"),
                    checkoutId.toString(), s.amount().amount(), s.amount().currency().code(), "POS checkout " + checkoutId));
            if (!p.tenantId().equals(tenant) || p.amount().compareTo(s.amount().amount()) != 0
                    || !p.currency().equals(s.amount().currency().code()))
                throw new IllegalStateException("created payment obligation does not match checkout");
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
        PaymentSnapshot p = payments.find(tenant, s.paymentId())
                .orElseThrow(() -> new IllegalStateException("payment obligation missing; manual reconciliation required"));
        if (!p.tenantId().equals(tenant) || !p.paymentId().equals(s.paymentId())
                || p.amount().compareTo(s.amount().amount()) != 0 || !p.currency().equals(s.amount().currency().code()))
            throw new IllegalStateException("payment obligation does not match checkout");
        if (p.status() == Status.SUCCEEDED) {
            if (s.status() != CheckoutSession.Status.PAID) s = repository.update(s.markPaid(clock.instant()), s.version());
            inventory.commit(tenant, s.reservationReference(), key(checkoutId, "commit-stock"));
            return repository.update(s.complete(clock.instant()), s.version());
        }
        if (p.status() == Status.FAILED || p.status() == Status.CANCELLED || p.status() == Status.EXPIRED) {
            inventory.release(tenant, s.reservationReference(), key(checkoutId, "release-stock"));
            return repository.update(s.cancel(clock.instant()), s.version());
        }
        if (p.status() == Status.UNKNOWN && s.status() != CheckoutSession.Status.PAYMENT_UNKNOWN)
            return repository.update(s.markUnknown(clock.instant()), s.version());
        return s;
    }

    private static String key(UUID id, String operation) { return "pos:" + id + ":" + operation; }
    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("tenantId cannot be blank");
        return value.trim();
    }
}
