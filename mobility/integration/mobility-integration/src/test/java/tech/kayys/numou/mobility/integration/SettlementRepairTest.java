package tech.kayys.numou.mobility.integration;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.mobility.pricing.PaymentMethod;
import tech.kayys.numou.mobility.ride.spi.PaymentsPort;
import tech.kayys.numou.mobility.wallet.domain.OwnerKind;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Payments that fail part-way through settlement: the repair path must finish the job exactly once. */
class SettlementRepairTest extends MobilityTestBase {

    /** Fails the first {@code failures} calls to moveDriver (after the rider has already been charged). */
    static final AtomicInteger REMAINING_FAILURES = new AtomicInteger();

    SettlementRepairTest() {
        super(real -> new PaymentsPort() {
            public Uni<Void> authorize(UUID t, UUID r, String h, BigDecimal a) { return real.authorize(t, r, h, a); }
            public Uni<Void> releaseHold(UUID t, UUID r, String h, String ref) { return real.releaseHold(t, r, h, ref); }
            public Uni<Boolean> riderCanBook(UUID t, UUID r) { return real.riderCanBook(t, r); }
            public Uni<Boolean> driverCanAcceptCash(UUID t, UUID d) { return real.driverCanAcceptCash(t, d); }
            public Uni<Void> chargeRider(UUID t, UUID r, String h, BigDecimal a, String ref, String m) { return real.chargeRider(t, r, h, a, ref, m); }
            public Uni<Void> creditRider(UUID t, UUID r, BigDecimal a, String ref, String m) { return real.creditRider(t, r, a, ref, m); }
            public Uni<Void> debitRiderStrict(UUID t, UUID r, BigDecimal a, String ref, String m) { return real.debitRiderStrict(t, r, a, ref, m); }
            public Uni<Void> moveDriver(UUID t, UUID d, BigDecimal a, String ref, String m) {
                if (REMAINING_FAILURES.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                    return Uni.createFrom().failure(new IllegalStateException("payment provider down"));
                }
                return real.moveDriver(t, d, a, ref, m);
            }
            public Uni<Void> movePlatform(UUID t, UUID k, BigDecimal a, String ref, String m) { return real.movePlatform(t, k, a, ref, m); }
        });
    }

    @Test
    void aSettlementThatFailsHalfwayIsRepairedBySweepingAndNeverDoubleCharges() {
        REMAINING_FAILURES.set(2);                                      // the event-triggered attempt and the inline attempt both fail
        final UUID rider = rider(200_000); final UUID driver = carDriver(-300);
        final var quote = carQuote(rider);
        final var booked = book(rider, quote, PaymentMethod.WALLET);
        pickUp(rider, driver, booked.id());
        driveNorth(driver, 8, 462.5, 100);
        s.rides().complete(tenant, driver, booked.id()).await().indefinitely();

        // the trip is over and the rider has been charged, but the driver's credit and the platform's share are still pending
        assertThat(balance(OwnerKind.RIDER, rider)).isEqualByComparingTo(new BigDecimal("200000").subtract(quote.total()));
        assertThat(balance(OwnerKind.DRIVER, driver)).isEqualByComparingTo("0");
        assertThat(rides.findEndedUnsettled(tenant, 10).await().indefinitely()).hasSize(1);

        assertThat(s.settlement().sweepUnsettled(tenant).await().indefinitely()).isEqualTo(1);        // the safety net finishes it
        final BigDecimal commission = quote.subtotal().multiply(new BigDecimal("0.20")).setScale(0, java.math.RoundingMode.HALF_UP);
        assertThat(balance(OwnerKind.DRIVER, driver)).isEqualByComparingTo(quote.subtotal().subtract(commission));
        assertThat(balance(OwnerKind.RIDER, rider)).isEqualByComparingTo(new BigDecimal("200000").subtract(quote.total()));   // charged once, not twice
        assertThat(s.wallets().platformBalance(tenant).await().indefinitely())
                .isEqualByComparingTo(quote.total().subtract(quote.subtotal().subtract(commission)));
        assertThat(rides.findEndedUnsettled(tenant, 10).await().indefinitely()).isEmpty();
        assertThat(s.settlement().sweepUnsettled(tenant).await().indefinitely()).isZero();
    }
}
