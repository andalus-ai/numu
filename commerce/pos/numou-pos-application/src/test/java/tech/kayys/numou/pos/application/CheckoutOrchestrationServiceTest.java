package tech.kayys.numou.pos.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.pos.application.port.out.*;
import tech.kayys.numou.pos.domain.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutOrchestrationServiceTest {
    private static final String TENANT = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final UUID CHECKOUT = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID SALE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant NOW = Instant.parse("2026-11-02T00:00:00Z");
    private final Money total = Money.of(new BigDecimal("125.00"), Currency.of("IDR"));

    private Sale openSale() {
        Sale sale = Sale.open(SALE_ID, UUID.fromString(TENANT),
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                SaleChannel.POS, "IDR", NOW.minusSeconds(60));
        return sale.addLine(new SaleLine(UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "item-1", "Test item", "SKU-1", BigDecimal.ONE, "EA", total));
    }

    @Test void startIsIdempotentAcrossRetries() {
        Fixture f = new Fixture(CheckoutPaymentPort.Status.PENDING);
        CheckoutSession result = f.service.start(TENANT, CHECKOUT, SALE_ID);
        assertEquals(CheckoutSession.Status.PAYMENT_PENDING, result.status());
        assertEquals(total, result.amount());
        assertEquals("reservation-1", result.reservationReference());
        assertEquals("payment-1", result.paymentId());
        f.service.start(TENANT, CHECKOUT, SALE_ID);
        assertEquals(1, f.reserveCalls);
        assertEquals(1, f.createPaymentCalls);
        assertEquals(1, f.quoteCalls);
    }

    @Test void checkoutRejectsMissingOrNonOpenSaleBeforeReservingStock() {
        Fixture missing = new Fixture(CheckoutPaymentPort.Status.PENDING);
        missing.sale = null;
        assertThrows(IllegalArgumentException.class, () -> missing.service.start(TENANT, CHECKOUT, SALE_ID));
        assertEquals(0, missing.reserveCalls);

        Fixture completed = new Fixture(CheckoutPaymentPort.Status.PENDING);
        completed.sale = completed.sale.complete(NOW);
        assertThrows(IllegalStateException.class, () -> completed.service.start(TENANT, CHECKOUT, SALE_ID));
        assertEquals(0, completed.reserveCalls);
    }

    @Test void checkoutRejectsQuoteCurrencyMismatchBeforeReservingStock() {
        Fixture f = new Fixture(CheckoutPaymentPort.Status.PENDING);
        f.quote = Money.of(new BigDecimal("125.00"), Currency.of("USD"));
        assertThrows(IllegalStateException.class, () -> f.service.start(TENANT, CHECKOUT, SALE_ID));
        assertEquals(0, f.reserveCalls);
    }

    @Test void stockIsCommittedOnlyAfterPaymentSuccess() {
        Fixture f = new Fixture(CheckoutPaymentPort.Status.PENDING);
        f.service.start(TENANT, CHECKOUT, SALE_ID);
        assertEquals(CheckoutSession.Status.PAYMENT_PENDING, f.service.reconcile(TENANT, CHECKOUT).status());
        assertEquals(0, f.commitCalls);
        f.paymentStatus = CheckoutPaymentPort.Status.SUCCEEDED;
        assertEquals(CheckoutSession.Status.COMPLETED, f.service.reconcile(TENANT, CHECKOUT).status());
        assertEquals(1, f.commitCalls);
    }

    @Test void unknownPaymentDoesNotReleaseOrCommitStock() {
        Fixture f = new Fixture(CheckoutPaymentPort.Status.UNKNOWN);
        f.service.start(TENANT, CHECKOUT, SALE_ID);
        assertEquals(CheckoutSession.Status.PAYMENT_UNKNOWN, f.service.reconcile(TENANT, CHECKOUT).status());
        assertEquals(0, f.releaseCalls);
        assertEquals(0, f.commitCalls);
    }

    @Test void failedPaymentReleasesReservation() {
        Fixture f = new Fixture(CheckoutPaymentPort.Status.FAILED);
        f.service.start(TENANT, CHECKOUT, SALE_ID);
        assertEquals(CheckoutSession.Status.CANCELLED, f.service.reconcile(TENANT, CHECKOUT).status());
        assertEquals(1, f.releaseCalls);
    }

    @Test void recoveryWorkerProcessesOnlyTenantScopedBoundedCandidates() {
        Fixture f = new Fixture(CheckoutPaymentPort.Status.SUCCEEDED);
        f.service.start(TENANT, CHECKOUT, SALE_ID);
        CheckoutRecoveryService worker = new CheckoutRecoveryService(f.repository, f.service);
        CheckoutRecoveryService.RecoveryReport report = worker.recoverTenant(TENANT, 10);
        assertEquals(1, report.attempted());
        assertEquals(1, report.completed());
        assertEquals(0, report.errors());
    }

    private final class Fixture {
        final Map<UUID, CheckoutSession> sessions = new HashMap<>();
        final CheckoutRepository repository;
        int reserveCalls, commitCalls, releaseCalls, createPaymentCalls, quoteCalls;
        CheckoutPaymentPort.Status paymentStatus;
        Sale sale = openSale();
        Money quote = total;
        final CheckoutOrchestrationService service;

        Fixture(CheckoutPaymentPort.Status status) {
            paymentStatus = status;
            repository = new CheckoutRepository() {
                public Optional<CheckoutSession> find(String tenant, UUID id) {
                    return Optional.ofNullable(sessions.get(id)).filter(s -> s.tenantId().equals(tenant));
                }
                public List<CheckoutSession> findRecoverable(String tenant, int limit) {
                    return sessions.values().stream().filter(s -> s.tenantId().equals(tenant))
                            .filter(s -> s.status() == CheckoutSession.Status.PAYMENT_PENDING
                                    || s.status() == CheckoutSession.Status.PAYMENT_UNKNOWN
                                    || s.status() == CheckoutSession.Status.PAID
                                    || s.status() == CheckoutSession.Status.COMPENSATION_REQUIRED)
                            .limit(limit).toList();
                }
                public CheckoutSession create(CheckoutSession s) {
                    if (sessions.putIfAbsent(s.id(), s) != null) throw new IllegalStateException("duplicate");
                    return s;
                }
                public CheckoutSession update(CheckoutSession s, long expected) {
                    CheckoutSession old = sessions.get(s.id());
                    if (old == null || !old.tenantId().equals(s.tenantId()) || old.version() != expected)
                        throw new IllegalStateException("version conflict");
                    sessions.put(s.id(), s); return s;
                }
            };
            CheckoutSalePort salePort = (tenant, id) -> Optional.ofNullable(sale)
                    .filter(value -> value.id().equals(id) && value.tenantId().toString().equals(tenant));
            CheckoutPricingPort pricingPort = (tenant, persistedSale) -> {
                quoteCalls++;
                return quote;
            };
            CheckoutInventoryPort stock = new CheckoutInventoryPort() {
                public Reservation reserve(String tenant, UUID checkout, UUID saleId, String key) {
                    reserveCalls++; return new Reservation("reservation-1");
                }
                public void commit(String tenant, String ref, String key) { commitCalls++; }
                public void release(String tenant, String ref, String key) { releaseCalls++; }
            };
            CheckoutPaymentPort payment = new CheckoutPaymentPort() {
                public PaymentSnapshot createOrGet(PaymentRequest request) {
                    createPaymentCalls++;
                    return snapshot(request.tenantId(), "payment-1", request.amount(), request.currency());
                }
                public Optional<PaymentSnapshot> find(String tenant, String id) {
                    return Optional.of(snapshot(tenant, id, total.amount(), total.currency().code()));
                }
                private PaymentSnapshot snapshot(String tenant, String id, BigDecimal amount, String currency) {
                    return new PaymentSnapshot(tenant, id, amount, currency, paymentStatus);
                }
            };
            service = new CheckoutOrchestrationService(repository, salePort, pricingPort, stock, payment,
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }
}
