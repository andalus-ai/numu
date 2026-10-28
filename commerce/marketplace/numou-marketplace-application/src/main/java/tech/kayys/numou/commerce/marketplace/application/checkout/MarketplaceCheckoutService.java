package tech.kayys.numou.commerce.marketplace.application.checkout;

import tech.kayys.numou.commerce.marketplace.application.port.MarketplaceOrderAuthorization;
import tech.kayys.numou.commerce.marketplace.application.port.MarketplaceOrderRepository;
import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrder;
import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrderLine;
import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrderStatus;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Durable checkout coordinator. It never owns stock or payment ledgers; all
 * side effects use idempotent capability ports and every durable checkpoint is versioned.
 */
public final class MarketplaceCheckoutService {
    private final MarketplaceOrderRepository orders;
    private final MarketplaceCheckoutRepository checkouts;
    private final MarketplaceOrderAuthorization authorization;
    private final MarketplaceCheckoutInventoryPort inventory;
    private final MarketplaceCheckoutPaymentPort payments;
    private final Clock clock;

    public MarketplaceCheckoutService(MarketplaceOrderRepository orders,
                                      MarketplaceCheckoutRepository checkouts,
                                      MarketplaceOrderAuthorization authorization,
                                      MarketplaceCheckoutInventoryPort inventory,
                                      MarketplaceCheckoutPaymentPort payments, Clock clock) {
        this.orders = Objects.requireNonNull(orders, "orders");
        this.checkouts = Objects.requireNonNull(checkouts, "checkouts");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.payments = Objects.requireNonNull(payments, "payments");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public MarketplaceCheckout checkout(String tenantId, String actorId, UUID orderId,
                                        String idempotencyKey, String warehouseReference,
                                        Instant reservationExpiresAt) {
        require(tenantId, "tenantId");
        require(actorId, "actorId");
        require(idempotencyKey, "idempotencyKey");
        require(warehouseReference, "warehouseReference");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(reservationExpiresAt, "reservationExpiresAt");
        Instant now = clock.instant();
        if (!reservationExpiresAt.isAfter(now)) throw new IllegalArgumentException("reservation expiry must be in the future");

        MarketplaceOrder order = orders.findByTenantAndId(tenantId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Marketplace order not found"));
        authorization.requireBuyer(tenantId, actorId, order);
        if (order.status() != MarketplaceOrderStatus.SELLER_ACCEPTED) {
            throw new IllegalStateException("Checkout requires a seller-accepted order");
        }

        String fingerprint = fingerprint(order, warehouseReference, reservationExpiresAt);
        MarketplaceCheckout proposed = MarketplaceCheckout.start(
                UUID.randomUUID(), tenantId, orderId, idempotencyKey, fingerprint, now);
        MarketplaceCheckout checkout = checkouts.createOrGet(proposed);
        if (!checkout.orderId().equals(orderId) || !checkout.requestFingerprint().equals(fingerprint)) {
            throw new IllegalStateException("Checkout idempotency key conflicts with a different request");
        }
        if (checkout.status() == MarketplaceCheckoutStatus.READY_FOR_FULFILLMENT
                || checkout.status() == MarketplaceCheckoutStatus.FAILED
                || checkout.status() == MarketplaceCheckoutStatus.COMPENSATION_REQUIRED) return checkout;

        if (checkout.status() == MarketplaceCheckoutStatus.RECONCILIATION_REQUIRED) {
            return reconcile(tenantId, checkout);
        }
        if (checkout.status() == MarketplaceCheckoutStatus.RESERVING_INVENTORY) {
            checkout = reserveAll(order, checkout, warehouseReference, reservationExpiresAt);
            if (checkout.status() != MarketplaceCheckoutStatus.RESERVING_INVENTORY) return checkout;
        }
        if (total(order).signum() == 0) {
            return save(checkout, checkout.readyWithoutPayment(clock.instant()));
        }

        MarketplaceCheckoutPaymentPort.PaymentResult payment;
        try {
            if (checkout.paymentId() == null) {
                payment = payments.createOrGet(tenantId, paymentKey(checkout.id()),
                        order.id().toString(), total(order), order.currency());
            } else {
                payment = payments.find(tenantId, checkout.paymentId())
                        .orElseThrow(() -> new IllegalStateException("Payment operation not found for persisted checkout"));
            }
        } catch (RuntimeException uncertain) {
            return save(checkout, checkout.reconciliationRequired(
                    "PAYMENT_OUTCOME_UNKNOWN", "Payment call outcome is uncertain; reservations retained for reconciliation",
                    clock.instant()));
        }
        return applyPaymentResult(tenantId, checkout, payment);
    }

    private MarketplaceCheckout reserveAll(MarketplaceOrder order, MarketplaceCheckout initial,
                                           String warehouseReference, Instant expiresAt) {
        MarketplaceCheckout checkout = initial;
        for (MarketplaceOrderLine line : order.lines()) {
            if (checkout.reservationIds().containsKey(line.lineId())) continue;
            String key = reservationKey(checkout.id(), line.lineId());
            String reservationId;
            try {
                reservationId = inventory.reserve(order.tenantId(), warehouseReference, line,
                        line.quantity(), expiresAt, key);
            } catch (MarketplaceCheckoutInventoryPort.ReservationRejectedException rejected) {
                return compensateKnownReservations(checkout, "INVENTORY_RESERVATION_REJECTED",
                        "Inventory authoritatively rejected a reservation; known reservations are being released");
            } catch (RuntimeException uncertain) {
                // A generic exception may follow a committed reservation. Never infer that no stock was reserved.
                try {
                    var recovered = inventory.findReservationIdByCommandKey(order.tenantId(), key);
                    if (recovered.isPresent()) {
                        MarketplaceCheckout next = checkout.recordReservation(line.lineId(), recovered.get(), clock.instant());
                        checkout = save(checkout, next);
                    }
                } catch (RuntimeException recoveryFailure) {
                    uncertain.addSuppressed(recoveryFailure);
                }
                return save(checkout, checkout.reconciliationRequired(
                        "RESERVATION_OUTCOME_UNKNOWN",
                        "Reservation command outcome is uncertain; inventory must reconcile before payment",
                        clock.instant()));
            }
            MarketplaceCheckout next = checkout.recordReservation(line.lineId(), recovered.get(), clock.instant());
                        checkout = save(checkout, next);
                    }
                } catch (RuntimeException recoveryFailure) {
                    return save(checkout, checkout.reconciliationRequired(
                            "RESERVATION_OUTCOME_UNKNOWN",
                            "Reservation outcome is uncertain; retry/reconcile using the same command key",
                            clock.instant()));
                }
                return compensateKnownReservations(checkout, "INVENTORY_RESERVATION_FAILED",
                        "Inventory reservation failed; known reservations are being released");
            }
            MarketplaceCheckout next = checkout.recordReservation(line.lineId(), reservationId, clock.instant());
            checkout = save(checkout, next);
        }
        return checkout;
    }

    private MarketplaceCheckout applyPaymentResult(String tenantId, MarketplaceCheckout checkout,
                                                    MarketplaceCheckoutPaymentPort.PaymentResult payment) {
        return switch (payment.state()) {
            case SUCCEEDED -> {
                MarketplaceCheckout awaiting = checkout.status() == MarketplaceCheckoutStatus.AWAITING_PAYMENT
                        ? checkout : save(checkout, checkout.awaitingPayment(payment.paymentId(), clock.instant()));
                yield save(awaiting, awaiting.ready(clock.instant()));
            }
            case FAILED, CANCELLED, EXPIRED -> compensateKnownReservations(checkout.awaitingPayment(
                    payment.paymentId(), clock.instant()), "PAYMENT_TERMINAL_FAILURE",
                    "Payment ended without success: " + payment.state());
            case CREATED, REQUIRES_CUSTOMER_ACTION, PENDING -> save(checkout,
                    checkout.awaitingPayment(payment.paymentId(), clock.instant()));
            case UNKNOWN -> save(checkout, checkout.awaitingPayment(payment.paymentId(), clock.instant())
                    .reconciliationRequired("PAYMENT_STATUS_UNKNOWN",
                            "Payment provider status is unknown; inventory reservations retained", clock.instant()));
        };
    }

    private MarketplaceCheckout reconcile(String tenantId, MarketplaceCheckout checkout) {
        if ("RESERVATION_OUTCOME_UNKNOWN".equals(checkout.failureCode())) {
            // Do not start payment until inventory has been reconciled by the inventory adapter/operator.
            return checkout;
        }
        if (checkout.paymentId() == null) {
            // Reissuing the exact same idempotent create command is safe and resolves lost responses.
            MarketplaceOrder order = orders.findByTenantAndId(tenantId, checkout.orderId())
                    .orElseThrow(() -> new IllegalArgumentException("Marketplace order not found"));
            try {
                var payment = payments.createOrGet(tenantId, paymentKey(checkout.id()),
                        order.id().toString(), total(order), order.currency());
                MarketplaceCheckout resumed = save(checkout, checkout.awaitingPayment(payment.paymentId(), clock.instant()));
                return applyPaymentResult(tenantId, resumed, payment);
            } catch (RuntimeException uncertain) {
                return checkout;
            }
        }
        try {
            var result = payments.find(tenantId, checkout.paymentId())
                    .orElseThrow(() -> new IllegalStateException("Payment operation not found"));
            MarketplaceCheckout resumed = save(checkout, checkout.awaitingPayment(result.paymentId(), clock.instant()));
            return applyPaymentResult(tenantId, resumed, result);
        } catch (RuntimeException unresolved) {
            return checkout;
        }
    }

    private MarketplaceCheckout compensateKnownReservations(MarketplaceCheckout checkout, String code, String detail) {
        for (var entry : checkout.reservationIds().entrySet()) {
            try {
                inventory.release(checkout.tenantId(), entry.getValue(),
                        "marketplace-checkout-release:" + checkout.id() + ":" + entry.getKey());
            } catch (RuntimeException releaseFailure) {
                return save(checkout, checkout.compensationRequired("RESERVATION_RELEASE_FAILED",
                        "Could not release every reservation; operator reconciliation required", clock.instant()));
            }
        }
        return save(checkout, checkout.fail(code, detail, clock.instant()));
    }

    private MarketplaceCheckout save(MarketplaceCheckout current, MarketplaceCheckout next) {
        if (!checkouts.saveIfVersion(current.tenantId(), current.id(), current.version(), next)) {
            throw new IllegalStateException("Checkout changed concurrently; reload and retry the same idempotency key");
        }
        return next.withVersion(current.version() + 1);
    }

    private static BigDecimal total(MarketplaceOrder order) {
        return order.lines().stream().map(line -> line.quantity().multiply(line.unitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String fingerprint(MarketplaceOrder order, String warehouse, Instant expiresAt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            field(digest, order.tenantId());
            field(digest, order.id().toString());
            field(digest, warehouse);
            field(digest, expiresAt.toString());
            for (MarketplaceOrderLine line : order.lines()) {
                field(digest, line.lineId());
                field(digest, line.productId());
                field(digest, line.quantity().stripTrailingZeros().toPlainString());
                field(digest, line.unit());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to fingerprint checkout request", e);
        }
    }

    private static void field(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String reservationKey(UUID checkoutId, String lineId) {
        return "marketplace-checkout:" + checkoutId + ":reserve:" + lineId;
    }

    private static String paymentKey(UUID checkoutId) {
        return "marketplace-checkout:" + checkoutId + ":payment";
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
