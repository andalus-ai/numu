package tech.kayys.numou.mobility.pricing;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingTest {

    static final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");
    static final FareSchedule S = FareSchedule.indonesiaDefaults();
    static final BigDecimal ONE = new BigDecimal("1.0");

    static BigDecimal bd(String s) { return new BigDecimal(s); }

    static FareQuote quote(double meters, long seconds, BigDecimal surge, Promo promo) {
        return FareCalculator.quote(S, VehicleType.CAR, meters, seconds, surge, promo, NOW, Duration.ofMinutes(5));
    }

    // ---- quote ----

    @Test
    void carFareIsBasePlusDistancePlusTimePlusBookingFee() {
        final var q = quote(5_000, 900, ONE, null);  // 5 km, 15 min
        assertThat(q.base()).isEqualByComparingTo("5000");
        assertThat(q.distanceFare()).isEqualByComparingTo("17500");   // 5 x 3,500
        assertThat(q.timeFare()).isEqualByComparingTo("6000");        // 15 x 400
        assertThat(q.subtotal()).isEqualByComparingTo("28500");
        assertThat(q.total()).isEqualByComparingTo("29500");          // + 1,000 booking fee
        assertThat(q.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void shortTripsPayTheMinimumFare() {
        final var q = quote(600, 180, ONE, null);
        assertThat(q.subtotal()).isEqualByComparingTo("14000");
        assertThat(q.minimumFareAdjustment()).isPositive();
    }

    @Test
    void surgeMultipliesTheFareButNotTheBookingFee() {
        final var q = quote(5_000, 900, bd("1.5"), null);
        assertThat(q.subtotal()).isEqualByComparingTo("43000");   // 28,500 x 1.5 = 42,750, rounded up to 43,000
        assertThat(q.bookingFee()).isEqualByComparingTo("1000");
        assertThat(q.total()).isEqualByComparingTo("44000");
    }

    @Test
    void fareComponentsAlwaysAddUpExactly() {
        final var rnd = new Random(7);
        for (int i = 0; i < 3000; i++) {
            final var surge = BigDecimal.valueOf(10 + rnd.nextInt(16)).divide(BigDecimal.TEN, 1, java.math.RoundingMode.UNNECESSARY);
            final var q = quote(200 + rnd.nextInt(40_000), 60 + rnd.nextInt(5_000), surge, null);
            assertThat(q.base().add(q.distanceFare()).add(q.timeFare()).add(q.minimumFareAdjustment()).add(q.surgeAmount())).isEqualByComparingTo(q.subtotal());
            assertThat(q.subtotal().subtract(q.discount()).add(q.bookingFee())).isEqualByComparingTo(q.total());
            assertThat(q.subtotal().remainder(bd("500"))).isEqualByComparingTo("0"); // always a multiple of the rounding unit
        }
    }

    @Test
    void invalidTripsAndSurgeAreRejected() {
        assertThatThrownBy(() -> quote(0, 60, ONE, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> quote(1000, 60, bd("0.9"), null)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- promos ----

    static Promo percent(String pct, String cap, String min) {
        return new Promo("SAVE", Promo.Type.PERCENT, bd(pct), cap == null ? null : bd(cap), bd(min), NOW.minusSeconds(60), NOW.plusSeconds(3600));
    }

    @Test
    void percentPromoIsCappedAndNeverTouchesTheBookingFee() {
        final var q = quote(5_000, 900, ONE, percent("50", "10000", "0"));
        assertThat(q.discount()).isEqualByComparingTo("10000");    // 50% = 14,250 capped at 10,000
        assertThat(q.total()).isEqualByComparingTo("19500");       // 28,500 - 10,000 + 1,000
    }

    @Test
    void promoCannotMakeTheFareNegative() {
        final var fixed = new Promo("BIG", Promo.Type.FIXED, bd("999999"), null, null, NOW.minusSeconds(1), NOW.plusSeconds(10));
        final var q = quote(600, 180, ONE, fixed);
        assertThat(q.discount()).isEqualByComparingTo(q.subtotal());
        assertThat(q.total()).isEqualByComparingTo("1000");        // only the booking fee remains
    }

    @Test
    void expiredOrBelowMinimumPromosGiveNothing() {
        final var expired = new Promo("OLD", Promo.Type.PERCENT, bd("20"), null, null, NOW.minusSeconds(7200), NOW.minusSeconds(3600));
        assertThat(quote(5_000, 900, ONE, expired).discount()).isEqualByComparingTo("0");
        assertThat(quote(5_000, 900, ONE, percent("20", null, "50000")).discount()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> percent("101", null, "0")).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- final fare: upfront vs metered ----

    @Test
    void tripWithinToleranceKeepsTheUpfrontPrice() {
        final var q = quote(5_000, 900, bd("1.5"), null);
        final var f = FareCalculator.finalFare(q, S, 5_400, 1_000, NOW);     // +8% distance, +11% time
        assertThat(f.basis()).isEqualTo(FinalFare.Basis.UPFRONT);
        assertThat(f.total()).isEqualByComparingTo(q.total());
    }

    @Test
    void bigDetourIsMeteredWithSurgeLockedAndCappedAtOneAndAHalfTimesTheQuote() {
        final var q = quote(5_000, 900, bd("1.5"), null);
        final var f = FareCalculator.finalFare(q, S, 20_000, 4_000, NOW);
        assertThat(f.basis()).isEqualTo(FinalFare.Basis.METERED);
        assertThat(f.subtotal()).isEqualByComparingTo("64500");             // cap: 43,000 x 1.5
        final var modest = FareCalculator.finalFare(q, S, 6_500, 1_300, NOW);  // +30% distance: metered, below cap
        assertThat(modest.basis()).isEqualTo(FinalFare.Basis.METERED);
        assertThat(modest.subtotal()).isGreaterThan(q.subtotal()).isLessThan(bd("64500"));
    }

    @Test
    void ridingMuchLessThanQuotedIsMeteredDownNotChargedInFull() {
        final var q = quote(10_000, 1_800, ONE, null);
        final var f = FareCalculator.finalFare(q, S, 3_000, 600, NOW);
        assertThat(f.basis()).isEqualTo(FinalFare.Basis.METERED);
        assertThat(f.subtotal()).isLessThan(q.subtotal());
        assertThat(f.subtotal()).isGreaterThanOrEqualTo(bd("14000"));       // never below the minimum fare
    }

    @Test
    void meteredFareKeepsThePromoCappedByTheNewSubtotal() {
        final var q = quote(5_000, 900, ONE, percent("100", null, "0"));
        final var f = FareCalculator.finalFare(q, S, 1_000, 300, NOW);
        assertThat(f.discount()).isLessThanOrEqualTo(f.subtotal());
        assertThat(f.total()).isGreaterThanOrEqualTo(bd("1000"));
    }

    // ---- surge ----

    @Test
    void surgeTiersAndNoiseFloor() {
        final var p = SurgePolicy.defaults();
        assertThat(p.target(4, 1)).isEqualByComparingTo("1.0");     // below minDemand: noise
        assertThat(p.target(10, 10)).isEqualByComparingTo("1.0");   // ratio 1.0
        assertThat(p.target(15, 10)).isEqualByComparingTo("1.2");   // 1.5
        assertThat(p.target(20, 10)).isEqualByComparingTo("1.5");   // 2.0
        assertThat(p.target(35, 10)).isEqualByComparingTo("2.0");   // 3.5
        assertThat(p.target(500, 10)).isEqualByComparingTo("2.5");  // capped by the top tier
        assertThat(p.target(10, 0)).isEqualByComparingTo("2.5");    // no drivers at all: ratio uses supply 1
    }

    @Test
    void surgeMovesInStepsAndNeverBelowOne() {
        final var p = SurgePolicy.defaults();
        assertThat(p.next(bd("1.0"), bd("2.5"))).isEqualByComparingTo("1.3");
        assertThat(p.next(bd("2.4"), bd("2.5"))).isEqualByComparingTo("2.5");
        assertThat(p.next(bd("2.5"), bd("1.0"))).isEqualByComparingTo("2.0");
        assertThat(p.next(bd("1.2"), bd("1.0"))).isEqualByComparingTo("1.0");
    }

    // ---- cancellation ----

    @Test
    void riderCancellationFeeRules() {
        final var pol = CancellationPolicy.defaults(); final var rate = S.rate(VehicleType.CAR);
        assertThat(pol.riderCancellationFee(rate, null, NOW, 0)).isEqualByComparingTo("0");                          // nobody assigned yet
        assertThat(pol.riderCancellationFee(rate, NOW, NOW.plusSeconds(100), 0)).isEqualByComparingTo("0");           // inside free window
        assertThat(pol.riderCancellationFee(rate, NOW, NOW.plusSeconds(121), 0)).isEqualByComparingTo("8000");        // after it
    }

    @Test
    void noShowOnlyAfterTheDriverHasWaitedLongEnough() {
        final var pol = CancellationPolicy.defaults();
        assertThat(pol.noShowAllowed(null, NOW)).isFalse();
        assertThat(pol.noShowAllowed(NOW, NOW.plusSeconds(299))).isFalse();
        assertThat(pol.noShowAllowed(NOW, NOW.plusSeconds(300))).isTrue();
        assertThat(pol.noShowFee(S.rate(VehicleType.CAR), 0)).isGreaterThan(S.rate(VehicleType.CAR).cancellationFee());
    }

    @Test
    void feeSplitAddsUpExactly() {
        final var split = CancellationPolicy.defaults().split(bd("8000"), 0);
        assertThat(split.driverPart()).isEqualByComparingTo("6400");
        assertThat(split.driverPart().add(split.platformPart())).isEqualByComparingTo("8000");
        final var odd = CancellationPolicy.defaults().split(bd("12345"), 0);
        assertThat(odd.driverPart().add(odd.platformPart())).isEqualByComparingTo("12345");
    }

    // ---- settlement ----

    @Test
    void settlementWorkedExample() {
        // F=43,000 D=8,600 B=1,000 T=2,000 commission 20%
        final var s = Settlement.compute(bd("43000"), bd("8600"), bd("1000"), bd("2000"), bd("0.20"), PaymentMethod.WALLET, 0);
        assertThat(s.riderPays()).isEqualByComparingTo("37400");
        assertThat(s.commission()).isEqualByComparingTo("8600");
        assertThat(s.driverEarning()).isEqualByComparingTo("36400");        // promo and booking fee do not reduce the driver
        assertThat(s.platformNet()).isEqualByComparingTo("1000");           // 8,600 + 1,000 - 8,600
        assertThat(s.riderWalletMove()).isEqualByComparingTo("-37400");
        assertThat(s.net()).isEqualByComparingTo("0");
    }

    @Test
    void cashRideMakesTheDriverOweThePlatformItsShare() {
        final var s = Settlement.compute(bd("30000"), BigDecimal.ZERO, bd("1000"), BigDecimal.ZERO, bd("0.20"), PaymentMethod.CASH, 0);
        assertThat(s.riderWalletMove()).isEqualByComparingTo("0");
        assertThat(s.driverWalletMove()).isEqualByComparingTo("-7000");     // collected 31,000 cash, is owed 24,000 => owes 7,000
        assertThat(s.platformWalletMove()).isEqualByComparingTo("7000");
        assertThat(s.net()).isEqualByComparingTo("0");
    }

    @Test
    void heavyPromoCanMakeThePlatformPayInstead() {
        final var s = Settlement.compute(bd("40000"), bd("30000"), bd("1000"), BigDecimal.ZERO, bd("0.10"), PaymentMethod.CASH, 0);
        assertThat(s.platformNet()).isNegative();                           // 4,000 + 1,000 - 30,000
        assertThat(s.driverWalletMove()).isPositive();                      // driver collected only 11,000 cash but earned 36,000
        assertThat(s.net()).isEqualByComparingTo("0");
    }

    @Test
    void moneyIsConservedForEveryCombination() {
        final var rnd = new Random(99);
        for (int i = 0; i < 20_000; i++) {
            final var fare = BigDecimal.valueOf(500L * (1 + rnd.nextInt(400)));
            final var disc = BigDecimal.valueOf((long) (rnd.nextDouble() * fare.doubleValue())).setScale(0, java.math.RoundingMode.DOWN);
            final var s = Settlement.compute(fare, disc, BigDecimal.valueOf(rnd.nextInt(3) * 500L), BigDecimal.valueOf(rnd.nextInt(4) * 1000L),
                    BigDecimal.valueOf(rnd.nextInt(31)).divide(BigDecimal.valueOf(100)), rnd.nextBoolean() ? PaymentMethod.WALLET : PaymentMethod.CASH, 0);
            assertThat(s.net()).isEqualByComparingTo("0");
            assertThat(s.riderPays().subtract(s.driverEarning())).isEqualByComparingTo(s.platformNet());
            assertThat(s.driverEarning()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }
    }

    @Test
    void feeSettlementAlsoConserves() {
        final var split = CancellationPolicy.defaults().split(bd("8000"), 0);
        final var s = Settlement.forFee(bd("8000"), split, 0);
        assertThat(s.riderWalletMove()).isEqualByComparingTo("-8000");
        assertThat(s.net()).isEqualByComparingTo("0");
    }

    @Test
    void invalidSettlementInputsAreRejected() {
        assertThatThrownBy(() -> Settlement.compute(bd("100"), bd("200"), BigDecimal.ZERO, BigDecimal.ZERO, bd("0.2"), PaymentMethod.WALLET, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Settlement.compute(bd("100"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, bd("1.2"), PaymentMethod.WALLET, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
