package tech.kayys.numou.commerce.marketplace.application.checkout;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.commerce.marketplace.application.port.MarketplaceOrderAuthorization;
import tech.kayys.numou.commerce.marketplace.application.port.MarketplaceOrderRepository;
import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrder;
import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrderLine;
import tech.kayys.numou.commerce.marketplace.domain.MarketplaceOrderStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MarketplaceCheckoutServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");

    @Test
    void successfulPaymentMakesCheckoutReadyWithoutOwningPaymentOrInventoryLedgers() {
        Fixture f = new Fixture(MarketplaceCheckoutPaymentPort.PaymentState.SUCCEEDED, false);
        MarketplaceCheckout result = f.service.checkout("tenant-a", "buyer-actor", f.order.id(),
                "checkout-key", "warehouse-1", NOW.plusSeconds(900));

        assertEquals(MarketplaceCheckoutStatus.READY_FOR_FULFILLMENT, result.status());
        assertEquals(1, f.inventory.reservations.size());
        assertEquals("payment-1", result.paymentId());
        assertTrue(f.inventory.released.isEmpty());
    }

    @Test
    void zeroTotalOrderDoesNotCreatePaymentObligation() {
        Fixture f = new Fixture(MarketplaceCheckoutPaymentPort.PaymentState.SUCCEEDED, false);
        MarketplaceOrder freeOrder = acceptedOrder(List.of(new MarketplaceOrderLine("free-line", "sample",
                "offer-sample", "seller-1", "Free sample", BigDecimal.ONE, "item",
                BigDecimal.ZERO, "IDR")));
        f.orders.orders.put(freeOrder.id(), freeOrder);

        MarketplaceCheckout result = f.service.checkout("tenant-a", "buyer-actor", freeOrder.id(),
                "checkout-free", "warehouse-1", NOW.plusSeconds(900));

        assertEquals(MarketplaceCheckoutStatus.READY_FOR_FULFILLMENT, result.status());
        assertNull(result.paymentId());
        assertEquals(0, f.payments.createCalls);
    }

    @Test
    void unknownPaymentOutcomeRetainsReservationAndRequiresReconciliation() {
        Fixture f = new Fixture(MarketplaceCheckoutPaymentPort.PaymentState.UNKNOWN, false);
        MarketplaceCheckout result = f.service.checkout("tenant-a", "buyer-actor", f.order.id(),
                "checkout-key", "warehouse-1", NOW.plusSeconds(900));

        assertEquals(MarketplaceCheckoutStatus.RECONCILIATION_REQUIRED, result.status());
        assertEquals("payment-1", result.paymentId());
        assertTrue(f.inventory.released.isEmpty(), "unknown payment outcome must not trigger release");
    }

    @Test
    void recoveryWorkerRetriesAmbiguousPaymentWithoutReleasingReservations() {
        Fixture f = new Fixture(MarketplaceCheckoutPaymentPort.PaymentState.UNKNOWN, false);
        MarketplaceCheckout started = f.service.checkout("tenant-a", "buyer-actor", f.order.id(),
                "checkout-recovery", "warehouse-1", NOW.plusSeconds(900));
        assertEquals(MarketplaceCheckoutStatus.RECONCILIATION_REQUIRED, started.status());

        MarketplaceCheckoutRecoveryService worker = new MarketplaceCheckoutRecoveryService(f.checkouts, f.service);
        MarketplaceCheckoutRecoveryService.RecoveryReport report = worker.recoverTenant("tenant-a", 20);

        assertEquals(1, report.attempted());
        assertEquals(1, report.reconciliationRequired());
        assertEquals(0, report.errors());
        assertTrue(f.inventory.released.isEmpty());
    }

    @Test
    void ambiguousReservationOutcomeBlocksPaymentAndDoesNotReleaseStock() {
        Fixture f = new Fixture(MarketplaceCheckoutPaymentPort.PaymentState.SUCCEEDED, false, true);
        MarketplaceOrder twoLineOrder = acceptedOrder(List.of(line("line-1", "produce-1"),
                line("line-2", "produce-2")));
        f.orders.orders.put(twoLineOrder.id(), twoLineOrder);

        MarketplaceCheckout result = f.service.checkout("tenant-a", "buyer-actor", twoLineOrder.id(),
                "checkout-key-ambiguous", "warehouse-1", NOW.plusSeconds(900));

        assertEquals(MarketplaceCheckoutStatus.RECONCILIATION_REQUIRED, result.status());
        assertEquals(0, f.payments.createCalls);
        assertTrue(f.inventory.released.isEmpty(), "uncertain reservation must not be treated as rejection");
    }

    @Test
    void failedReservationReleasesKnownReservationsAndNeverCreatesPayment() {
        Fixture f = new Fixture(MarketplaceCheckoutPaymentPort.PaymentState.SUCCEEDED, true);
        MarketplaceOrder twoLineOrder = acceptedOrder(List.of(line("line-1", "produce-1"),
                line("line-2", "produce-2")));
        f.orders.orders.put(twoLineOrder.id(), twoLineOrder);

        MarketplaceCheckout result = f.service.checkout("tenant-a", "buyer-actor", twoLineOrder.id(),
                "checkout-key-2", "warehouse-1", NOW.plusSeconds(900));

        assertEquals(MarketplaceCheckoutStatus.FAILED, result.status());
        assertEquals(1, f.inventory.released.size());
        assertEquals(0, f.payments.createCalls);
    }

    private static MarketplaceOrder acceptedOrder(List<MarketplaceOrderLine> lines) {
        return MarketplaceOrder.create(UUID.randomUUID(), "tenant-a", "buyer-1", lines, "IDR", NOW)
                .submit(NOW.plusSeconds(1)).sellerAccept(NOW.plusSeconds(2));
    }

    private static MarketplaceOrderLine line(String lineId, String productId) {
        return new MarketplaceOrderLine(lineId, productId, "offer-" + productId, "seller-1",
                "Fresh produce", BigDecimal.ONE, "kg", new BigDecimal("10000"), "IDR");
    }

    private static final class Fixture {
        final FakeOrders orders = new FakeOrders();
        final FakeCheckouts checkouts = new FakeCheckouts();
        final FakeInventory inventory;
        final FakePayments payments;
        final MarketplaceOrder order = acceptedOrder(List.of(line("line-1", "produce-1")));
        final MarketplaceCheckoutService service;

        Fixture(MarketplaceCheckoutPaymentPort.PaymentState paymentState, boolean failSecondLine) {
            this(paymentState, failSecondLine, false);
        }
        Fixture(MarketplaceCheckoutPaymentPort.PaymentState paymentState, boolean failSecondLine,
                boolean ambiguousSecondLine) {
            orders.orders.put(order.id(), order);
            inventory = new FakeInventory(failSecondLine, ambiguousSecondLine);
            payments = new FakePayments(paymentState);
            MarketplaceOrderAuthorization auth = new MarketplaceOrderAuthorization() {
                @Override public void requireBuyer(String tenant, String actor, MarketplaceOrder value) {
                    if (!tenant.equals(value.tenantId()) || !actor.equals("buyer-actor"))
                        throw new SecurityException("unauthorized");
                }
                @Override public void requireSeller(String tenant, String actor, String seller, MarketplaceOrder value) {
                    throw new UnsupportedOperationException();
                }
                @Override public void requireFulfillmentOperator(String tenant, String actor, MarketplaceOrder value) {
                    throw new UnsupportedOperationException();
                }
            };
            service = new MarketplaceCheckoutService(orders, checkouts, auth, inventory, payments,
                    Clock.fixed(NOW.plusSeconds(10), ZoneOffset.UTC));
        }
    }

    private static final class FakeOrders implements MarketplaceOrderRepository {
        final Map<UUID, MarketplaceOrder> orders = new HashMap<>();
        @Override public void create(MarketplaceOrder order) { orders.put(order.id(), order); }
        @Override public Optional<MarketplaceOrder> findByTenantAndId(String tenant, UUID id) {
            return Optional.ofNullable(orders.get(id)).filter(o -> o.tenantId().equals(tenant));
        }
        @Override public boolean replaceIfCurrentStatus(String tenant, UUID id, MarketplaceOrderStatus expected,
                                                        MarketplaceOrder replacement) { throw new UnsupportedOperationException(); }
    }

    private static final class FakeCheckouts implements MarketplaceCheckoutRepository {
        final Map<String, MarketplaceCheckout> byKey = new HashMap<>();
        final Map<UUID, MarketplaceCheckout> byId = new HashMap<>();
        @Override public synchronized MarketplaceCheckout createOrGet(MarketplaceCheckout proposed) {
            String key = proposed.tenantId() + ":" + proposed.idempotencyKey();
            MarketplaceCheckout existing = byKey.get(key);
            if (existing != null) {
                if (!existing.requestFingerprint().equals(proposed.requestFingerprint()))
                    throw new IllegalStateException("idempotency conflict");
                return existing;
            }
            byKey.put(key, proposed); byId.put(proposed.id(), proposed); return proposed;
        }
        @Override public Optional<MarketplaceCheckout> findByTenantAndId(String tenant, UUID id) {
            return Optional.ofNullable(byId.get(id)).filter(c -> c.tenantId().equals(tenant));
        }
        @Override public Optional<MarketplaceCheckout> findByTenantAndKey(String tenant, String key) {
            return Optional.ofNullable(byKey.get(tenant + ":" + key));
        }
        @Override public List<MarketplaceCheckout> findRecoverable(String tenant, int limit) {
            return byId.values().stream()
                    .filter(c -> c.tenantId().equals(tenant))
                    .filter(c -> c.status() == MarketplaceCheckoutStatus.AWAITING_PAYMENT
                            || c.status() == MarketplaceCheckoutStatus.RECONCILIATION_REQUIRED
                            || c.status() == MarketplaceCheckoutStatus.COMPENSATION_REQUIRED)
                    .limit(limit).toList();
        }
        @Override public synchronized boolean saveIfVersion(String tenant, UUID id, long expected, MarketplaceCheckout next) {
            MarketplaceCheckout current = byId.get(id);
            if (current == null || !current.tenantId().equals(tenant) || current.version() != expected) return false;
            MarketplaceCheckout saved = next.withVersion(expected + 1);
            byId.put(id, saved); byKey.put(tenant + ":" + saved.idempotencyKey(), saved); return true;
        }
    }

    private static final class FakeInventory implements MarketplaceCheckoutInventoryPort {
        final Map<String, String> reservations = new HashMap<>();
        final Map<String, String> byKey = new HashMap<>();
        final java.util.Set<String> released = new java.util.HashSet<>();
        final boolean failSecondLine;
        final boolean ambiguousSecondLine;
        FakeInventory(boolean failSecondLine, boolean ambiguousSecondLine) {
            this.failSecondLine = failSecondLine;
            this.ambiguousSecondLine = ambiguousSecondLine;
        }
        @Override public String reserve(String tenant, String warehouse, MarketplaceOrderLine line,
                                        BigDecimal quantity, Instant expiry, String key) {
            if (failSecondLine && line.lineId().equals("line-2")) throw new MarketplaceCheckoutInventoryPort.ReservationRejectedException("out of stock");
            if (ambiguousSecondLine && line.lineId().equals("line-2")) throw new IllegalStateException("reservation response timed out");
            return byKey.computeIfAbsent(tenant + ":" + key, k -> {
                String id = "reservation-" + reservations.size();
                reservations.put(id, line.lineId()); return id;
            });
        }
        @Override public Optional<String> findReservationIdByCommandKey(String tenant, String key) {
            return Optional.ofNullable(byKey.get(tenant + ":" + key));
        }
        @Override public void release(String tenant, String reservationId, String key) { released.add(reservationId); }
    }

    private static final class FakePayments implements MarketplaceCheckoutPaymentPort {
        final MarketplaceCheckoutPaymentPort.PaymentState state;
        int createCalls;
        FakePayments(MarketplaceCheckoutPaymentPort.PaymentState state) { this.state = state; }
        @Override public PaymentResult createOrGet(String tenant, String key, String order, BigDecimal amount, String currency) {
            createCalls++;
            return new PaymentResult("payment-1", state, NOW.plusSeconds(5));
        }
        @Override public Optional<PaymentResult> find(String tenant, String paymentId) {
            return Optional.of(new PaymentResult(paymentId, state, NOW.plusSeconds(5)));
        }
    }
}
