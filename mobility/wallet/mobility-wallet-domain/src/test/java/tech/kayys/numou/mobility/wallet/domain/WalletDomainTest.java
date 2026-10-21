package tech.kayys.numou.mobility.wallet.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletDomainTest {

    static final Instant T = Instant.parse("2026-10-10T10:00:00Z");
    static BigDecimal bd(String s) { return new BigDecimal(s); }
    static Wallet rider() { return Wallet.open(OwnerKind.RIDER, UUID.randomUUID(), "IDR", 0, bd("0"), T); }
    static Wallet driver() { return Wallet.open(OwnerKind.DRIVER, UUID.randomUUID(), "IDR", 0, bd("100000"), T); }

    @Test
    void creditIsIdempotentPerRef() {
        final var w = rider();
        assertThat(w.credit("topup:1", bd("50000"), T).applied()).isTrue();
        assertThat(w.credit("topup:1", bd("50000"), T).applied()).isFalse();   // gateway webhook delivered twice
        assertThat(w.balance()).isEqualByComparingTo("50000");
    }

    @Test
    void holdReservesAvailableBalanceAndCannotExceedIt() {
        final var w = rider(); w.credit("t", bd("30000"), T);
        w.hold("h1", bd("20000"), T);
        assertThat(w.available()).isEqualByComparingTo("10000");
        assertThat(w.balance()).isEqualByComparingTo("30000");
        assertThatThrownBy(() -> w.hold("h2", bd("10001"), T)).isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void captureConsumesTheHoldAndChargesTheFare() {
        final var w = rider(); w.credit("t", bd("50000"), T); w.hold("h", bd("30000"), T);
        final var r = w.capture("h", "cap", bd("28000"), T);
        assertThat(r.shortfall()).isEqualByComparingTo("0");
        assertThat(w.balance()).isEqualByComparingTo("22000");
        assertThat(w.holds()).isEmpty();
        assertThat(w.available()).isEqualByComparingTo("22000");     // the unused part of the hold is free again
    }

    @Test
    void captureBeyondTheHoldUsesBalanceThenGoesIntoDebtWithoutRefusing() {
        final var w = rider(); w.credit("t", bd("40000"), T); w.hold("h", bd("30000"), T);
        final var r = w.capture("h", "cap", bd("55000"), T);          // fare grew well past the estimate
        assertThat(w.balance()).isEqualByComparingTo("-15000");
        assertThat(r.shortfall()).isEqualByComparingTo("15000");
        assertThat(w.canBook()).isFalse();                            // blocked until they top up
        assertThat(w.pullDomainEvents()).extracting(e -> e.eventType()).contains("mobility.wallet.overdrawn");
        w.credit("t2", bd("15000"), T);
        assertThat(w.canBook()).isTrue();
    }

    @Test
    void releaseFreesTheHoldAndIsReplaySafe() {
        final var w = rider(); w.credit("t", bd("30000"), T); w.hold("h", bd("30000"), T);
        w.release("h", "rel", T);
        assertThat(w.available()).isEqualByComparingTo("30000");
        assertThat(w.release("h", "rel", T).applied()).isFalse();
        assertThat(w.release("h-missing", "rel2", T).applied()).isTrue();   // releasing a hold that never existed is harmless
    }

    @Test
    void debitRespectsTheOverdraftLimit() {
        final var d = driver();
        d.debit("c1", bd("60000"), T);                                // cash commission owed
        assertThat(d.canAcceptCash()).isTrue();
        d.debit("c2", bd("40000"), T);                                // exactly at the limit
        assertThat(d.balance()).isEqualByComparingTo("-100000");
        assertThat(d.canAcceptCash()).isFalse();
        assertThatThrownBy(() -> d.debit("c3", bd("1"), T)).isInstanceOf(InsufficientFundsException.class);
        d.credit("e", bd("50000"), T);                                // earnings pay the debt down
        assertThat(d.canAcceptCash()).isTrue();
    }

    @Test
    void riderCannotOverdrawByDebit() {
        final var w = rider(); w.credit("t", bd("1000"), T);
        assertThatThrownBy(() -> w.debit("d", bd("1001"), T)).isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void platformAccountsHaveNoLimit() {
        final var p = Wallet.open(OwnerKind.PLATFORM, UUID.randomUUID(), "IDR", 0, null, T);
        p.debit("promo", bd("99999999"), T);
        assertThat(p.balance()).isEqualByComparingTo("-99999999");
    }

    @Test
    void frozenWalletsCannotSpendOrBook() {
        final var w = rider(); w.credit("t", bd("10000"), T); w.freeze("fraud review", T);
        assertThat(w.canBook()).isFalse();
        assertThatThrownBy(() -> w.hold("h", bd("1000"), T)).isInstanceOf(InvalidStateException.class);
        assertThatThrownBy(() -> w.debit("d", bd("1000"), T)).isInstanceOf(InvalidStateException.class);
        w.credit("t2", bd("5000"), T);                                // money can still come in
        w.unfreeze(T);
        assertThat(w.canBook()).isTrue();
    }

    @Test
    void nonPositiveAmountsAreRejected() {
        final var w = rider();
        assertThatThrownBy(() -> w.credit("a", bd("0"), T)).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> w.debit("b", bd("-5"), T)).isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    void replayProtectionWindowIsBoundedButKeepsRecentRefs() {
        final var w = rider();
        for (int i = 0; i < Wallet.MAX_REFS + 50; i++) w.credit("r" + i, bd("1"), T);
        assertThat(w.snapshot().refs()).hasSize(Wallet.MAX_REFS);
        assertThat(w.credit("r" + (Wallet.MAX_REFS + 49), bd("1"), T).applied()).isFalse();   // recent: still deduplicated
    }

    @Test
    void walletIdIsDeterministicPerOwner() {
        final var owner = UUID.randomUUID();
        assertThat(WalletId.forOwner(OwnerKind.RIDER, owner)).isEqualTo(WalletId.forOwner(OwnerKind.RIDER, owner));
        assertThat(WalletId.forOwner(OwnerKind.RIDER, owner)).isNotEqualTo(WalletId.forOwner(OwnerKind.DRIVER, owner));
    }

    @Test
    void snapshotRoundTrip() {
        final var w = rider(); w.credit("t", bd("30000"), T); w.hold("h", bd("10000"), T);
        assertThat(Wallet.restore(w.snapshot()).snapshot()).isEqualTo(w.snapshot());
        assertThat(Wallet.restore(w.snapshot()).available()).isEqualByComparingTo("20000");
    }
}
