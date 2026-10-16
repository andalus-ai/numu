package tech.kayys.syirkah.workforce.domain.payroll;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.foundation.domain.valueobject.Currency;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.workforce.domain.employment.EmploymentId;
import tech.kayys.syirkah.workforce.domain.paycomponent.PayComponentId;
import tech.kayys.syirkah.workforce.domain.payroll.event.PayrollPeriodCreated;
import tech.kayys.syirkah.workforce.domain.payroll.event.PayrollRunApproved;
import tech.kayys.syirkah.workforce.domain.payroll.event.PayrollRunCreated;
import tech.kayys.syirkah.workforce.domain.payslip.Payslip;
import tech.kayys.syirkah.workforce.domain.payslip.PayslipId;
import tech.kayys.syirkah.workforce.domain.payslip.PayslipLine;
import tech.kayys.syirkah.workforce.domain.payslip.PayslipLineType;
import tech.kayys.syirkah.workforce.domain.payslip.PayslipStatus;
import tech.kayys.syirkah.workforce.domain.payslip.event.PayslipApproved;
import tech.kayys.syirkah.workforce.domain.payslip.event.PayslipGenerated;
import tech.kayys.syirkah.workforce.domain.worker.WorkerId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayrollAndPayslipTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final Currency IDR = Currency.of("IDR");
    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate END = LocalDate.of(2026, 9, 30);
    private static final LocalDate PAY_DATE = LocalDate.of(2026, 9, 30);

    @Test
    void payrollPeriod_lifecycleTransitions() {
        PayrollPeriod period = PayrollPeriod.create(
                PayrollPeriodId.generate(), TENANT, START, END, PAY_DATE);

        assertThat(period.status()).isEqualTo(PayrollPeriodStatus.OPEN);
        assertThat(period.contains(LocalDate.of(2026, 9, 15))).isTrue();
        assertThat(period.contains(LocalDate.of(2026, 10, 1))).isFalse();
        assertThat(period.pullDomainEvents()).hasSize(1)
                .first().isInstanceOf(PayrollPeriodCreated.class);

        period.startProcessing();
        assertThat(period.status()).isEqualTo(PayrollPeriodStatus.PROCESSING);

        period.finalizePeriod();
        assertThat(period.status()).isEqualTo(PayrollPeriodStatus.FINALIZED);

        period.close();
        assertThat(period.status()).isEqualTo(PayrollPeriodStatus.CLOSED);
    }

    @Test
    void payrollRun_lifecycleTransitions() {
        PayrollPeriodId periodId = PayrollPeriodId.generate();
        PayrollRun run = PayrollRun.create(
                PayrollRunId.generate(), periodId, TENANT, Instant.now());

        assertThat(run.status()).isEqualTo(PayrollRunStatus.DRAFT);
        assertThat(run.pullDomainEvents()).hasSize(1)
                .first().isInstanceOf(PayrollRunCreated.class);

        run.startCalculation();
        assertThat(run.status()).isEqualTo(PayrollRunStatus.CALCULATING);

        run.markCalculated();
        assertThat(run.status()).isEqualTo(PayrollRunStatus.CALCULATED);

        run.approve();
        assertThat(run.status()).isEqualTo(PayrollRunStatus.APPROVED);
        assertThat(run.pullDomainEvents()).hasSize(1)
                .first().isInstanceOf(PayrollRunApproved.class);

        run.post();
        assertThat(run.status()).isEqualTo(PayrollRunStatus.POSTED);

        assertThatThrownBy(run::cancel).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void payslip_derivedTotalsCalculation() {
        PayrollRunId runId = PayrollRunId.generate();
        PayrollPeriodId periodId = PayrollPeriodId.generate();
        WorkerId workerId = WorkerId.generate();
        EmploymentId employmentId = EmploymentId.generate();

        PayslipLine basicSalary = PayslipLine.of(
                PayComponentId.generate(), "BASIC", "Basic Salary",
                PayslipLineType.EARNING, Money.of(BigDecimal.valueOf(10_000_000), IDR));

        PayslipLine transport = PayslipLine.of(
                PayComponentId.generate(), "TRANS", "Transport Allowance",
                PayslipLineType.EARNING, Money.of(BigDecimal.valueOf(1_000_000), IDR));

        PayslipLine tax = PayslipLine.of(
                PayComponentId.generate(), "TAX", "Income Tax Deduction",
                PayslipLineType.DEDUCTION, Money.of(BigDecimal.valueOf(500_000), IDR));

        Payslip payslip = Payslip.generate(
                PayslipId.generate(), runId, workerId, employmentId, periodId, IDR,
                List.of(basicSalary, transport, tax));

        assertThat(payslip.status()).isEqualTo(PayslipStatus.DRAFT);
        assertThat(payslip.grossPay()).isEqualTo(Money.of(BigDecimal.valueOf(11_000_000), IDR));
        assertThat(payslip.totalDeductions()).isEqualTo(Money.of(BigDecimal.valueOf(500_000), IDR));
        assertThat(payslip.netPay()).isEqualTo(Money.of(BigDecimal.valueOf(10_500_000), IDR));
        assertThat(payslip.pullDomainEvents()).hasSize(1)
                .first().isInstanceOf(PayslipGenerated.class);

        payslip.approve();
        assertThat(payslip.status()).isEqualTo(PayslipStatus.APPROVED);
        assertThat(payslip.pullDomainEvents()).hasSize(1)
                .first().isInstanceOf(PayslipApproved.class);

        payslip.markPaid();
        assertThat(payslip.status()).isEqualTo(PayslipStatus.PAID);
    }
}
