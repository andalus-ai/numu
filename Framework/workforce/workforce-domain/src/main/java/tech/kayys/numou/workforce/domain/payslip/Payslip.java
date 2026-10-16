package tech.kayys.numou.workforce.domain.payslip;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.payroll.PayrollPeriodId;
import tech.kayys.numou.workforce.domain.payroll.PayrollRunId;
import tech.kayys.numou.workforce.domain.payslip.event.PayslipApproved;
import tech.kayys.numou.workforce.domain.payslip.event.PayslipGenerated;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Payslip aggregate root — the formal pay calculation result for a worker in a payroll run.
 *
 * <p>Business key: payrollRunId + employmentId (must be unique per run).
 * <p>Gross, deductions, and net are derived — never stored independently.
 */
public final class Payslip extends AbstractAggregateRoot<PayslipId> {

    private final PayrollRunId payrollRunId;
    private final WorkerId workerId;
    private final EmploymentId employmentId;
    private final PayrollPeriodId periodId;
    private final Currency currency;
    private final List<PayslipLine> lines;
    private PayslipStatus status;

    private Payslip(
            PayslipId id,
            PayrollRunId payrollRunId,
            WorkerId workerId,
            EmploymentId employmentId,
            PayrollPeriodId periodId,
            Currency currency
    ) {
        super(id);
        this.payrollRunId = Objects.requireNonNull(payrollRunId, "payrollRunId must not be null");
        this.workerId = Objects.requireNonNull(workerId, "workerId must not be null");
        this.employmentId = Objects.requireNonNull(employmentId, "employmentId must not be null");
        this.periodId = Objects.requireNonNull(periodId, "periodId must not be null");
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
        this.lines = new ArrayList<>();
        this.status = PayslipStatus.DRAFT;
    }

    public static Payslip generate(
            PayslipId id,
            PayrollRunId payrollRunId,
            WorkerId workerId,
            EmploymentId employmentId,
            PayrollPeriodId periodId,
            Currency currency,
            List<PayslipLine> lines
    ) {
        Objects.requireNonNull(lines, "lines must not be null");
        Payslip payslip = new Payslip(id, payrollRunId, workerId, employmentId, periodId, currency);
        lines.forEach(payslip.lines::add);
        payslip.raise(new PayslipGenerated(id, payrollRunId, periodId, workerId, employmentId));
        return payslip;
    }

    public void approve() {
        if (status != PayslipStatus.DRAFT) {
            throw new IllegalStateException("Payslip is not in DRAFT status");
        }
        this.status = PayslipStatus.APPROVED;
        raise(new PayslipApproved(getId(), workerId));
    }

    public void markPaid() {
        if (status != PayslipStatus.APPROVED) {
            throw new IllegalStateException("Payslip must be APPROVED before marking as PAID");
        }
        this.status = PayslipStatus.PAID;
    }

    // ---- Derived totals ----

    public Money grossPay() {
        return lines.stream()
                .filter(PayslipLine::isEarning)
                .map(PayslipLine::amount)
                .reduce(Money.zero(currency), Money::add);
    }

    public Money totalDeductions() {
        return lines.stream()
                .filter(PayslipLine::isDeduction)
                .map(PayslipLine::amount)
                .reduce(Money.zero(currency), Money::add);
    }

    public Money netPay() {
        return grossPay().subtract(totalDeductions());
    }

    public List<PayslipLine> earnings() {
        return lines.stream().filter(PayslipLine::isEarning).toList();
    }

    public List<PayslipLine> deductions() {
        return lines.stream().filter(PayslipLine::isDeduction).toList();
    }

    public PayrollRunId payrollRunId() { return payrollRunId; }
    public WorkerId workerId() { return workerId; }
    public EmploymentId employmentId() { return employmentId; }
    public PayrollPeriodId periodId() { return periodId; }
    public Currency currency() { return currency; }
    public List<PayslipLine> lines() { return Collections.unmodifiableList(lines); }
    public PayslipStatus status() { return status; }
}
