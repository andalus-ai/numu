package tech.kayys.numou.education.school.domain;

import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.school.domain.event.FeeInvoiceIssued;
import tech.kayys.numou.education.school.domain.event.FeePaymentRecorded;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Monthly school fee (SPP) invoice for one student. Amounts are in rupiah and kept at two decimals.
 * Overpayment is rejected; voiding is only possible before any payment.
 */
public final class FeeInvoice extends AbstractAggregateRoot<FeeInvoiceId> {

    private final TenantId tenantId;
    private final StudentId studentId;
    private final YearMonth period;
    private final BigDecimal amount;
    private final LocalDate dueDate;
    private final List<FeePayment> payments = new ArrayList<>();
    private FeeStatus status = FeeStatus.UNPAID;

    private FeeInvoice(FeeInvoiceId id, TenantId tenantId, StudentId studentId, YearMonth period, BigDecimal amount, LocalDate dueDate) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.studentId = Objects.requireNonNull(studentId, "studentId must not be null");
        this.period = Objects.requireNonNull(period, "period must not be null");
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Invoice amount must be positive");
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.dueDate = Objects.requireNonNull(dueDate, "dueDate must not be null");
    }

    public static FeeInvoice issue(FeeInvoiceId id, TenantId tenantId, StudentId studentId, YearMonth period,
                                   BigDecimal amount, LocalDate dueDate) {
        FeeInvoice i = new FeeInvoice(id, tenantId, studentId, period, amount, dueDate);
        i.raise(new FeeInvoiceIssued(id, tenantId, studentId, period, i.amount));
        return i;
    }

    public void pay(BigDecimal paid, LocalDate paidOn, String method) {
        if (status == FeeStatus.VOID) throw new IllegalStateException("Invoice is void");
        if (status == FeeStatus.PAID) throw new IllegalStateException("Invoice is already fully paid");
        FeePayment payment = new FeePayment(paid.setScale(2, RoundingMode.HALF_UP), paidOn, method);
        if (payment.amount().compareTo(outstanding()) > 0) {
            throw new IllegalArgumentException("Payment exceeds outstanding balance of " + outstanding().toPlainString());
        }
        payments.add(payment);
        status = outstanding().signum() == 0 ? FeeStatus.PAID : FeeStatus.PARTIAL;
        touch();
        raise(new FeePaymentRecorded(getId(), tenantId, studentId, payment.amount(), status == FeeStatus.PAID));
    }

    public void voidInvoice() {
        if (!payments.isEmpty()) throw new IllegalStateException("Invoices with payments cannot be voided");
        status = FeeStatus.VOID;
        touch();
    }

    public BigDecimal paidTotal() {
        return payments.stream().map(FeePayment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isVoid() { return status == FeeStatus.VOID; }

    public BigDecimal outstanding() { return amount.subtract(paidTotal()); }

    public boolean isOverdue(LocalDate today) {
        return (status == FeeStatus.UNPAID || status == FeeStatus.PARTIAL) && today.isAfter(dueDate);
    }

    private void touch() {
        incrementVersion();
        updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public StudentId getStudentId() { return studentId; }
    public YearMonth getPeriod() { return period; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getDueDate() { return dueDate; }
    public FeeStatus getStatus() { return status; }
    public List<FeePayment> getPayments() { return Collections.unmodifiableList(payments); }
}
