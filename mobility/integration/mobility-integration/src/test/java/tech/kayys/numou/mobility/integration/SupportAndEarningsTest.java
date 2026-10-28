package tech.kayys.numou.mobility.integration;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.pricing.PaymentMethod;
import tech.kayys.numou.wallet.application.EarningsStatement;
import tech.kayys.numou.wallet.application.EarningsStatement.Category;
import tech.kayys.numou.wallet.domain.LedgerEntry;
import tech.kayys.numou.wallet.domain.OwnerKind;
import tech.kayys.numou.wallet.domain.WalletId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportAndEarningsTest extends MobilityTestBase {

    static BigDecimal bd(String s) { return new BigDecimal(s); }

    UUID completedRide(UUID rider, UUID driver, PaymentMethod method) {
        final var booked = book(rider, carQuote(rider), method);
        pickUp(rider, driver, booked.id());
        driveNorth(driver, 8, 462.5, 100);
        s.rides().complete(tenant, driver, booked.id()).await().indefinitely();
        return booked.id();
    }

    // ---------------------------------------------------------------- refunds

    @Test
    void aRefundCreditsTheRiderAndIsPaidByThePlatformAndIsIdempotentPerCase() {
        final UUID rider = rider(200_000); final UUID d = carDriver(-300);
        final UUID ride = completedRide(rider, d, PaymentMethod.WALLET);
        final BigDecimal before = balance(OwnerKind.RIDER, rider);
        final BigDecimal platformBefore = s.wallets().platformBalance(tenant).await().indefinitely();

        final var first = s.support().refund(tenant, ride, bd("5000"), BigDecimal.ZERO, "CASE-100").await().indefinitely();
        final var again = s.support().refund(tenant, ride, bd("5000"), BigDecimal.ZERO, "CASE-100").await().indefinitely();   // support double-clicked
        assertThat(first.refunded()).isEqualByComparingTo("5000");
        assertThat(again.refunded()).isEqualByComparingTo("5000");
        assertThat(balance(OwnerKind.RIDER, rider)).isEqualByComparingTo(before.add(bd("5000")));
        assertThat(s.wallets().platformBalance(tenant).await().indefinitely()).isEqualByComparingTo(platformBefore.subtract(bd("5000")));
        assertThat(bus.types()).contains("mobility.ride.refunded");
    }

    @Test
    void theDriverCanBeChargedPartOfTheRefund() {
        final UUID rider = rider(200_000); final UUID d = carDriver(-300);
        final UUID ride = completedRide(rider, d, PaymentMethod.WALLET);
        final BigDecimal driverBefore = balance(OwnerKind.DRIVER, d); final BigDecimal platformBefore = s.wallets().platformBalance(tenant).await().indefinitely();
        s.support().refund(tenant, ride, bd("6000"), bd("2000"), "CASE-200").await().indefinitely();
        assertThat(balance(OwnerKind.DRIVER, d)).isEqualByComparingTo(driverBefore.subtract(bd("2000")));
        assertThat(s.wallets().platformBalance(tenant).await().indefinitely()).isEqualByComparingTo(platformBefore.subtract(bd("4000")));
    }

    @Test
    void refundsCannotExceedWhatWasPaidAcrossAllCases() {
        final UUID rider = rider(200_000); final UUID d = carDriver(-300);
        final UUID ride = completedRide(rider, d, PaymentMethod.WALLET);
        final BigDecimal paid = s.rides().get(tenant, tech.kayys.numou.mobility.ride.application.Actor.rider(rider), ride).await().indefinitely().finalTotal();
        s.support().refund(tenant, ride, paid.subtract(bd("1000")), BigDecimal.ZERO, "A").await().indefinitely();
        assertThatThrownBy(() -> s.support().refund(tenant, ride, bd("1001"), BigDecimal.ZERO, "B").await().indefinitely()).hasMessageContaining("exceeds");
        final var rest = s.support().refund(tenant, ride, bd("1000"), BigDecimal.ZERO, "C").await().indefinitely();
        assertThat(rest.refundable()).isEqualByComparingTo("0");
    }

    @Test
    void invalidRefundRequestsAreRefused() {
        final UUID rider = rider(200_000); final UUID d = carDriver(-300);
        final UUID ride = completedRide(rider, d, PaymentMethod.WALLET);
        assertThatThrownBy(() -> s.support().refund(tenant, ride, bd("100"), BigDecimal.ZERO, " ").await().indefinitely()).hasMessageContaining("reference");
        assertThatThrownBy(() -> s.support().refund(tenant, ride, bd("100"), bd("200"), "X").await().indefinitely()).hasMessageContaining("driver share");
        assertThatThrownBy(() -> s.support().refund(tenant, UUID.randomUUID(), bd("100"), BigDecimal.ZERO, "Y").await().indefinitely())
                .isInstanceOf(tech.kayys.numou.aggregate.domain.NotFoundException.class);
        assertThatThrownBy(() -> s.support().refund(UUID.randomUUID(), ride, bd("100"), BigDecimal.ZERO, "Z").await().indefinitely())
                .isInstanceOf(tech.kayys.numou.aggregate.domain.NotFoundException.class);                       // another tenant's support cannot touch it
        final UUID r2 = rider(100_000);
        final var searching = book(r2, carQuote(r2), PaymentMethod.WALLET);
        assertThatThrownBy(() -> s.support().refund(tenant, searching.id(), bd("100"), BigDecimal.ZERO, "W").await().indefinitely())
                .isInstanceOf(tech.kayys.numou.foundation.domain.exception.InvalidStateException.class);        // nothing was paid yet
    }

    // ---------------------------------------------------------------- earnings statement

    @Test
    void aStatementSeparatesEarningsCashCommissionTipsAndPayoutsAndMatchesTheBalance() {
        final UUID d = carDriver(-300);
        final UUID r1 = rider(200_000), r2 = rider(0);
        final Instant from = now.get().minusSeconds(60);
        final UUID walletRide = completedRide(r1, d, PaymentMethod.WALLET);
        advance(120); ping(d, north(MONAS, -300));
        completedRide(r2, d, PaymentMethod.CASH);
        s.rides().tip(tenant, r1, walletRide, bd("3000")).await().indefinitely();
        s.wallets().credit(tenant, OwnerKind.DRIVER, d, bd("100000"), "topup:fixture", "test funding so the payout is possible").await().indefinitely();
        s.wallets().requestPayout(tenant, d, bd("60000"), "payout:p1").await().indefinitely();

        final var st = s.wallets().statement(tenant, OwnerKind.DRIVER, d, from, now.get().plusSeconds(60)).await().indefinitely();
        assertThat(st.trips()).isEqualTo(2);
        assertThat(st.totals().get(Category.RIDE_EARNINGS)).isPositive();
        assertThat(st.totals().get(Category.CASH_COMMISSION)).isNegative();
        assertThat(st.totals().get(Category.TIPS)).isEqualByComparingTo("3000");
        assertThat(st.totals().get(Category.PAYOUTS)).isEqualByComparingTo("-60000");
        assertThat(st.totals().get(Category.OTHER)).isEqualByComparingTo("100000");
        assertThat(st.net()).isEqualByComparingTo(st.closingBalance().subtract(st.openingBalance()));
        assertThat(st.closingBalance()).isEqualByComparingTo(balance(OwnerKind.DRIVER, d));      // the statement can never disagree with the wallet
    }

    @Test
    void statementPeriodsAreBounded() {
        final UUID d = UUID.randomUUID();
        assertThatThrownBy(() -> s.wallets().statement(tenant, OwnerKind.DRIVER, d, now.get(), now.get().minusSeconds(1)).await().indefinitely()).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> s.wallets().statement(tenant, OwnerKind.DRIVER, d, now.get().minusSeconds(400L * 86_400), now.get()).await().indefinitely()).isInstanceOf(BusinessRuleViolation.class);
        assertThat(s.wallets().statement(tenant, OwnerKind.DRIVER, d, now.get().minusSeconds(86_400), now.get()).await().indefinitely().trips()).isZero();
    }

    @Test
    void classificationHandlesEveryReferenceKindAndIgnoresHolds() {
        final var w = WalletId.forOwner(OwnerKind.DRIVER, UUID.randomUUID()); final Instant t = Instant.parse("2026-10-10T10:00:00Z");
        final var ride = UUID.randomUUID();
        final var entries = List.of(
                LedgerEntry.of(w, LedgerEntry.Kind.CREDIT, bd("24000"), bd("24000"), "ride:" + ride + ":settle:driver", "", t, 0L),
                LedgerEntry.of(w, LedgerEntry.Kind.HOLD, bd("999"), bd("24000"), "ride:x:hold", "", t.plusSeconds(1), 1L),
                LedgerEntry.of(w, LedgerEntry.Kind.CREDIT, bd("500"), bd("24500"), "incentive:q1:" + ride, "", t.plusSeconds(2), 2L),
                LedgerEntry.of(w, LedgerEntry.Kind.CAPTURE, bd("-2000"), bd("22500"), "refund:CASE-1:driver", "", t.plusSeconds(3), 3L),
                LedgerEntry.of(w, LedgerEntry.Kind.DEBIT, bd("-10000"), bd("12500"), "payout:p2", "", t.plusSeconds(4), 4L),
                LedgerEntry.of(w, LedgerEntry.Kind.CREDIT, bd("10000"), bd("22500"), "payout-reversal:p2", "", t.plusSeconds(5), 5L),
                LedgerEntry.of(w, LedgerEntry.Kind.CREDIT, bd("6400"), bd("28900"), "ride:" + UUID.randomUUID() + ":fee:driver", "", t.plusSeconds(6), 6L));
        final var st = EarningsStatement.build(entries, t, t.plusSeconds(60), 0);
        assertThat(st.totals().get(Category.RIDE_EARNINGS)).isEqualByComparingTo("24000");
        assertThat(st.totals().get(Category.INCENTIVES)).isEqualByComparingTo("500");
        assertThat(st.totals().get(Category.REFUND_ADJUSTMENTS)).isEqualByComparingTo("-2000");
        assertThat(st.totals().get(Category.PAYOUTS)).isEqualByComparingTo("-10000");
        assertThat(st.totals().get(Category.PAYOUT_REVERSALS)).isEqualByComparingTo("10000");
        assertThat(st.totals().get(Category.CANCELLATION_FEES)).isEqualByComparingTo("6400");
        assertThat(st.net()).isEqualByComparingTo("28900");
        assertThat(st.openingBalance()).isEqualByComparingTo("0");
        assertThat(st.closingBalance()).isEqualByComparingTo("28900");
    }
}
