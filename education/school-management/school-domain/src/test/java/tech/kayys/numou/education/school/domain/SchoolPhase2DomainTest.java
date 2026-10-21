package tech.kayys.numou.education.school.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.education.core.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchoolPhase2DomainTest {

    private static final TenantId TENANT = TenantId.generate();

    private static LessonSlot slot(DayOfWeek d, int sh, int eh) {
        return LessonSlot.schedule(LessonSlotId.generate(), TENANT, ClassroomId.generate(), TeacherId.generate(), "MTK",
                d, LocalTime.of(sh, 0), LocalTime.of(eh, 0), null);
    }

    private static FeeInvoice invoice(String amount) {
        return FeeInvoice.issue(FeeInvoiceId.generate(), TENANT, StudentId.generate(), YearMonth.of(2025, 9),
                new BigDecimal(amount), LocalDate.of(2025, 9, 10));
    }

    @Test
    void lessonSlot_overlapRules() {
        LessonSlot a = slot(DayOfWeek.MONDAY, 7, 8);
        assertThat(a.overlaps(slot(DayOfWeek.MONDAY, 7, 9))).isTrue();
        assertThat(a.overlaps(slot(DayOfWeek.MONDAY, 8, 9))).isFalse();   // touching ends
        assertThat(a.overlaps(slot(DayOfWeek.TUESDAY, 7, 8))).isFalse();
    }

    @Test
    void lessonSlot_endMustFollowStart() {
        assertThatThrownBy(() -> slot(DayOfWeek.MONDAY, 9, 8)).hasMessageContaining("after start");
    }

    @Test
    void fee_partialThenFullPayment() {
        FeeInvoice i = invoice("500000");
        i.pay(new BigDecimal("200000"), LocalDate.of(2025, 9, 3), "TRANSFER");
        assertThat(i.getStatus()).isEqualTo(FeeStatus.PARTIAL);
        assertThat(i.outstanding().toPlainString()).isEqualTo("300000.00");
        i.pay(new BigDecimal("300000"), LocalDate.of(2025, 9, 5), "TUNAI");
        assertThat(i.getStatus()).isEqualTo(FeeStatus.PAID);
        assertThat(i.pullDomainEvents()).hasSize(3);
    }

    @Test
    void fee_rejectsOverpayment_andPaymentAfterPaid() {
        FeeInvoice i = invoice("100000");
        assertThatThrownBy(() -> i.pay(new BigDecimal("100001"), LocalDate.now(), "TUNAI")).hasMessageContaining("exceeds");
        i.pay(new BigDecimal("100000"), LocalDate.now(), "TUNAI");
        assertThatThrownBy(() -> i.pay(new BigDecimal("1"), LocalDate.now(), "TUNAI")).hasMessageContaining("fully paid");
    }

    @Test
    void fee_overdueAndVoid() {
        FeeInvoice i = invoice("100000");
        assertThat(i.isOverdue(LocalDate.of(2025, 9, 11))).isTrue();
        assertThat(i.isOverdue(LocalDate.of(2025, 9, 10))).isFalse();
        i.voidInvoice();
        assertThat(i.isOverdue(LocalDate.of(2025, 9, 11))).isFalse();
        assertThatThrownBy(() -> i.pay(new BigDecimal("1"), LocalDate.now(), "x")).hasMessageContaining("void");
    }

    @Test
    void fee_paidInvoiceCannotBeVoided() {
        FeeInvoice i = invoice("100000");
        i.pay(new BigDecimal("1000"), LocalDate.now(), "TUNAI");
        assertThatThrownBy(i::voidInvoice).hasMessageContaining("payments");
    }

    @Test
    void fee_rejectsNonPositiveAmounts() {
        assertThatThrownBy(() -> invoice("0")).isInstanceOf(IllegalArgumentException.class);
        FeeInvoice i = invoice("100");
        assertThatThrownBy(() -> i.pay(new BigDecimal("-5"), LocalDate.now(), "x")).isInstanceOf(IllegalArgumentException.class);
    }
}
