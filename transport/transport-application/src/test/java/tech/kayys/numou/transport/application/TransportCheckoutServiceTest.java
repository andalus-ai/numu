package tech.kayys.numou.transport.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.transport.domain.DepartureInventory;
import tech.kayys.numou.transport.domain.PassengerHoldStatus;
import tech.kayys.numou.transport.domain.TransportCheckout;
import tech.kayys.numou.transport.domain.TransportCheckoutStatus;
import tech.kayys.numou.transport.spi.DepartureInventoryRepository;
import tech.kayys.numou.transport.spi.InventoryConcurrencyException;
import tech.kayys.numou.transport.spi.TransportCheckoutConcurrencyException;
import tech.kayys.numou.transport.spi.TransportCheckoutRepository;
import tech.kayys.numou.transport.spi.TransportFareQuotePort;
import tech.kayys.numou.transport.spi.TransportPaymentPort;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransportCheckoutServiceTest {
    private final Instant start = Instant.parse("2026-10-28T02:00:00Z");
    private final MutableClock clock = new MutableClock(start);
    private final UUID departureId = UUID.randomUUID();
    private final MemoryInventoryRepository inventories = new MemoryInventoryRepository();
    private final MemoryCheckoutRepository checkouts = new MemoryCheckoutRepository();
    private final MemoryPayments payments = new MemoryPayments(clock);
    private final TransportFareQuotePort fares = request -> new TransportFareQuotePort.Quote(
            "quote-123", new BigDecimal("125000.00"), "IDR", start.plusSeconds(3600));
    private TransportCheckoutService service;

    @BeforeEach
    void setUp() {
        inventories.create(DepartureInventory.reconstitute("tenant-1", departureId, 20, 5, 0, List.of()));
        service = new TransportCheckoutService(new DepartureBookingService(inventories, clock),
                checkouts, fares, payments, clock, 900);
    }

    @Test
    void startsCheckoutWithServerQuoteAndReplaysPaymentIdempotently() {
        TransportCheckout first = service.start(command());
        TransportCheckout replay = service.start(command());

        assertThat(first.status()).isEqualTo(TransportCheckoutStatus.PAYMENT_PENDING);
        assertThat(first.amount()).isEqualByComparingTo("125000.00");
        assertThat(first.currency()).isEqualTo("IDR");
        assertThat(first.holdId()).isNotNull();
        assertThat(first.paymentId()).isNotBlank();
        assertThat(replay.checkoutId()).isEqualTo(first.checkoutId());
        assertThat(payments.createCount).isEqualTo(1);
        assertThat(inventories.find("tenant-1", departureId).orElseThrow().holds()).hasSize(1);
    }

    @Test
    void successfulPaymentConfirmsCapacityBeforeMarkingCheckoutPaid() {
        TransportCheckout checkout = service.start(command());
        payments.setStatus(checkout.paymentId(), TransportPaymentPort.PaymentState.SUCCEEDED);

        TransportCheckout settled = service.refresh("tenant-1", checkout.checkoutId());

        assertThat(settled.status()).isEqualTo(TransportCheckoutStatus.PAYMENT_SUCCEEDED);
        assertThat(inventories.find("tenant-1", departureId).orElseThrow().holds().getFirst().status())
                .isEqualTo(PassengerHoldStatus.CONFIRMED);
    }

    @Test
    void failedPaymentReleasesCapacityAndRemainsAuditable() {
        TransportCheckout checkout = service.start(command());
        payments.setStatus(checkout.paymentId(), TransportPaymentPort.PaymentState.FAILED);

        TransportCheckout settled = service.refresh("tenant-1", checkout.checkoutId());

        assertThat(settled.status()).isEqualTo(TransportCheckoutStatus.PAYMENT_FAILED);
        assertThat(inventories.find("tenant-1", departureId).orElseThrow().holds().getFirst().status())
                .isEqualTo(PassengerHoldStatus.RELEASED);
    }

    @Test
    void paymentSuccessAfterCapacityExpiresRequiresReconciliation() {
        TransportCheckout checkout = service.start(command());
        payments.setStatus(checkout.paymentId(), TransportPaymentPort.PaymentState.SUCCEEDED);
        clock.set(start.plusSeconds(901));

        TransportCheckout settled = service.refresh("tenant-1", checkout.checkoutId());

        assertThat(settled.status()).isEqualTo(TransportCheckoutStatus.RECONCILIATION_REQUIRED);
        assertThat(inventories.find("tenant-1", departureId).orElseThrow().holds().getFirst().status())
                .isEqualTo(PassengerHoldStatus.EXPIRED);
    }

    @Test
    void reconciliationPollsOldPendingCheckoutAndMovesRetryCursorForward() {
        TransportCheckout checkout = service.start(command());
        clock.set(start.plusSeconds(120));

        TransportCheckoutService.ReconciliationResult result =
                service.reconcileDue(java.time.Duration.ofSeconds(60), 10);

        assertThat(result.attempted()).isEqualTo(1);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        TransportCheckout refreshed = checkouts.find("tenant-1", checkout.checkoutId()).orElseThrow();
        assertThat(refreshed.updatedAt()).isEqualTo(clock.instant());
        assertThat(refreshed.version()).isGreaterThan(checkout.version());
    }

    @Test
    void idempotencyKeyCannotBeReusedForDifferentSegment() {
        service.start(command());

        assertThatThrownBy(() -> service.start(new TransportCheckoutService.StartCommand(
                "tenant-1", "checkout-key-1", departureId, 2, 4, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different checkout request");
    }

    @Test
    void checkoutLookupIsTenantScoped() {
        TransportCheckout checkout = service.start(command());

        assertThatThrownBy(() -> service.refresh("tenant-2", checkout.checkoutId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checkout not found");
    }

    private TransportCheckoutService.StartCommand command() {
        return new TransportCheckoutService.StartCommand("tenant-1", "checkout-key-1",
                departureId, 1, 3, 2);
    }

    private static final class MemoryInventoryRepository implements DepartureInventoryRepository {
        private final Map<String, DepartureInventory> data = new HashMap<>();

        @Override
        public Optional<DepartureInventory> find(String tenantId, UUID departureId) {
            return Optional.ofNullable(data.get(key(tenantId, departureId)));
        }

        @Override
        public List<InventoryKey> findDueInventories(Instant now, int limit) {
            return List.of();
        }

        @Override
        public void create(DepartureInventory inventory) {
            if (data.putIfAbsent(key(inventory.tenantId(), inventory.departureId()), inventory) != null) {
                throw new InventoryConcurrencyException("duplicate inventory");
            }
        }

        @Override
        public void save(DepartureInventory inventory, int expectedVersion) {
            String key = key(inventory.tenantId(), inventory.departureId());
            DepartureInventory current = data.get(key);
            if (current == null || current.version() != expectedVersion) {
                throw new InventoryConcurrencyException("stale inventory");
            }
            data.put(key, inventory);
        }

        private static String key(String tenant, UUID departure) {
            return tenant + ":" + departure;
        }
    }

    private static final class MemoryCheckoutRepository implements TransportCheckoutRepository {
        private final Map<String, TransportCheckout> byId = new HashMap<>();

        @Override
        public Optional<TransportCheckout> find(String tenantId, UUID checkoutId) {
            return Optional.ofNullable(byId.get(tenantId + ":" + checkoutId));
        }

        @Override
        public Optional<TransportCheckout> findByIdempotencyKey(String tenantId, String idempotencyKey) {
            return byId.values().stream().filter(c -> c.tenantId().equals(tenantId)
                    && c.idempotencyKey().equals(idempotencyKey)).findFirst();
        }

        @Override
        public Optional<TransportCheckout> findByPaymentId(String tenantId, String paymentId) {
            return byId.values().stream().filter(c -> c.tenantId().equals(tenantId)
                    && paymentId.equals(c.paymentId())).findFirst();
        }

        @Override
        public List<TransportCheckout> findStaleNonTerminal(Instant updatedBefore, int limit) {
            return byId.values().stream()
                    .filter(c -> c.updatedAt().isBefore(updatedBefore))
                    .filter(c -> c.status() == TransportCheckoutStatus.REQUESTED
                            || c.status() == TransportCheckoutStatus.HOLD_CREATED
                            || c.status() == TransportCheckoutStatus.PAYMENT_PENDING
                            || c.status() == TransportCheckoutStatus.PAYMENT_ACTION_REQUIRED)
                    .sorted(java.util.Comparator.comparing(TransportCheckout::updatedAt)
                            .thenComparing(TransportCheckout::tenantId)
                            .thenComparing(TransportCheckout::checkoutId))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void create(TransportCheckout checkout) {
            if (findByIdempotencyKey(checkout.tenantId(), checkout.idempotencyKey()).isPresent()
                    || byId.putIfAbsent(checkout.tenantId() + ":" + checkout.checkoutId(), checkout) != null) {
                throw new TransportCheckoutConcurrencyException("duplicate checkout");
            }
        }

        @Override
        public void save(TransportCheckout checkout, int expectedVersion) {
            String key = checkout.tenantId() + ":" + checkout.checkoutId();
            TransportCheckout current = byId.get(key);
            if (current == null || current.version() != expectedVersion) {
                throw new TransportCheckoutConcurrencyException("stale checkout");
            }
            byId.put(key, checkout);
        }
    }

    private static final class MemoryPayments implements TransportPaymentPort {
        private final Map<String, PaymentSnapshot> byId = new HashMap<>();
        private final Map<String, String> idByKey = new HashMap<>();
        private final Clock clock;
        private int createCount;

        private MemoryPayments(Clock clock) {
            this.clock = clock;
        }

        @Override
        public PaymentSnapshot createOrGet(CreatePayment command) {
            String existingId = idByKey.get(command.tenantId() + ":" + command.idempotencyKey());
            if (existingId != null) return byId.get(existingId);
            createCount++;
            String id = UUID.randomUUID().toString();
            PaymentSnapshot snapshot = new PaymentSnapshot(command.tenantId(), id, command.callerReference(),
                    command.amount(), command.currency(), PaymentState.PENDING, null, clock.instant());
            byId.put(id, snapshot);
            idByKey.put(command.tenantId() + ":" + command.idempotencyKey(), id);
            return snapshot;
        }

        @Override
        public PaymentSnapshot find(String tenantId, String paymentId) {
            PaymentSnapshot result = byId.get(paymentId);
            if (result == null || !result.tenantId().equals(tenantId)) {
                throw new IllegalArgumentException("payment not found");
            }
            return result;
        }

        private void setStatus(String paymentId, PaymentState status) {
            PaymentSnapshot current = byId.get(paymentId);
            byId.put(paymentId, new PaymentSnapshot(current.tenantId(), current.paymentId(),
                    current.callerReference(), current.amount(), current.currency(), status,
                    current.actionUri(), clock.instant()));
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        private MutableClock(Instant instant) { this.instant = new AtomicReference<>(instant); }
        void set(Instant value) { instant.set(value); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }
}
