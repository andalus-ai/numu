package tech.kayys.numou.education.pesantren.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PesantrenPhase2DomainTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final LocalDate DAY = LocalDate.of(2025, 9, 1);

    private static SantriWallet wallet(String limit) {
        return SantriWallet.open(SantriWalletId.generate(), TENANT, SantriId.generate(), limit == null ? null : new BigDecimal(limit));
    }

    @Test
    void wallet_depositAndSpendTrackBalance() {
        SantriWallet w = wallet(null);
        w.deposit(new BigDecimal("100000"), DAY, "Kiriman wali");
        w.spend(new BigDecimal("15000"), DAY, "Kantin");
        assertThat(w.getBalance().toPlainString()).isEqualTo("85000.00");
        assertThat(w.getTransactions()).hasSize(2);
        assertThat(w.pullDomainEvents()).hasSize(2);
    }

    @Test
    void wallet_neverGoesNegative() {
        SantriWallet w = wallet(null);
        w.deposit(new BigDecimal("10000"), DAY, null);
        assertThatThrownBy(() -> w.spend(new BigDecimal("10001"), DAY, "x")).hasMessageContaining("Insufficient");
        assertThat(w.getBalance().toPlainString()).isEqualTo("10000.00");
    }

    @Test
    void wallet_dailyLimitAppliesPerDay() {
        SantriWallet w = wallet("20000");
        w.deposit(new BigDecimal("100000"), DAY, null);
        w.spend(new BigDecimal("15000"), DAY, "a");
        assertThatThrownBy(() -> w.spend(new BigDecimal("6000"), DAY, "b")).hasMessageContaining("Daily spending limit");
        w.spend(new BigDecimal("6000"), DAY.plusDays(1), "next day is fine");
        assertThat(w.spentOn(DAY).toPlainString()).isEqualTo("15000.00");
    }

    @Test
    void wallet_rejectsNonPositiveAmounts() {
        SantriWallet w = wallet(null);
        assertThatThrownBy(() -> w.deposit(BigDecimal.ZERO, DAY, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> wallet("0")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void discipline_validatesFields() {
        DisciplineRecord r = DisciplineRecord.record(DisciplineRecordId.generate(), TENANT, SantriId.generate(), DAY,
                DisciplineCategory.RINGAN, "Terlambat jamaah subuh", 5, "Ustadz Hasan");
        assertThat(r.getPoints()).isEqualTo(5);
        assertThatThrownBy(() -> DisciplineRecord.record(DisciplineRecordId.generate(), TENANT, SantriId.generate(), DAY,
                DisciplineCategory.BERAT, " ", 5, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DisciplineRecord.record(DisciplineRecordId.generate(), TENANT, SantriId.generate(), DAY,
                DisciplineCategory.BERAT, "x", 0, "x")).isInstanceOf(IllegalArgumentException.class);
    }
}
