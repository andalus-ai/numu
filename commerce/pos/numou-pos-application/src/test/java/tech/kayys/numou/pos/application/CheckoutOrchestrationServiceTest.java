package tech.kayys.numou.pos.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.pos.application.port.out.*;
import tech.kayys.numou.pos.domain.CheckoutSession;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutOrchestrationServiceTest {
    private static final String TENANT = "tenant-1";
    private static final UUID CHECKOUT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID SALE = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant NOW = Instant.parse("2026-11-02T00:00:00Z");
    private final Money total = Money.of(new BigDecimal("125.00"), Currency.of("IDR"));

    @Test void startIsIdempotentAcrossRetries() {
        Fixture f = new Fixture(CheckoutPaymentPort.Status.PENDING);
        CheckoutSession result = f.service.start(TENANT, CHECKOUT, SALE, total);
        assertEquals(CheckoutSession.Status.PAYMENT_PENDING, result.status());
        assertEquals("reservation-1", result.reservationReference());
        assertEquals("payment-1", result.paymentId());
        f.service.start(TENANT, CHECKOUT, SALE, total);
        assertEquals(1, f.reserveCalls);
        assertEquals(1, f.createPaymentCalls);
    }

    @Test void stockIsCommittedOnlyAfterPaymentSuccess() {
        Fixture f = new Fixture(CheckoutPaymentPort.Status.PENDING);
        f.service.start(TENANT, CHECKOUT, SALE, total);
        assertEquals(CheckoutSession.Status.PAYMENT_PENDING, f.service.reconcile(TENANT, CHECKOUT).status());
        assertEquals(0, f.commitCalls);
        f.paymentStatus = CheckoutPaymentPort.Status.SUCCEEDED;
        assertEquals(CheckoutSession.Status.COMPLETED, f.service.reconcile(TENANT, CHECKOUT).status());
        assertEquals(1, f.commitCalls);
    }

    @Test void unknownPaymentDoesNotReleaseOrCommitStock() {
        Fixture f = new Fixture(CheckoutPaymentPort.Status.UNKNOWN);
        f.service.start(TENANT, CHECKOUT, SALE, total);
        assertEquals(CheckoutSession.Status.PAYMENT_UNKNOWN, f.service.reconcile(TENANT, CHECKOUT).status());
        assertEquals(0, f.releaseCalls);
        assertEquals(0, f.commitCalls);
    }

    @Test void failedPaymentReleasesReservation() {
        Fixture f = new Fixture(CheckoutPaymentPort.Status.FAILED);
        f.service.start(TENANT, CHECKOUT, SALE, total);
        assertEquals(CheckoutSession.Status.CANCELLED, f.service.reconcile(TENANT, CHECKOUT).status());
        assertEquals(1, f.releaseCalls);
    }

    private final class Fixture {
        final Map<UUID, CheckoutSession> sessions = new HashMap<>();
        int reserveCalls, commitCalls, releaseCalls, createPaymentCalls;
        CheckoutPaymentPort.Status paymentStatus;
        final CheckoutOrchestrationService service;

        Fixture(CheckoutPaymentPort.Status status) {
            paymentStatus = status;
            CheckoutRepository repository = new CheckoutRepository() {
                public Optional<CheckoutSession> find(String tenant, UUID id) {
                    return Optional.ofNullable(sessions.get(id)).filter(s -> s.tenantId().equals(tenant));
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
            CheckoutInventoryPort stock = new CheckoutInventoryPort() {
                public Reservation reserve(String tenant, UUID checkout, UUID sale, String key) {
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
            service = new CheckoutOrchestrationService(repository, stock, payment, Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }
}
