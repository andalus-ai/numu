package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.workforce.domain.worker.WorkerId;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.payroll.PayrollRunId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CompletionStage;

/**
 * SPI port that resolves and applies payroll rule sets.
 * Implementations handle tenant/country-specific tax rules, pay-component policies,
 * overtime rules, and other payroll compliance concerns without leaking into the domain.
 */
public interface PayrollRuleSet {

    /**
     * Returns the applicable tax rate for the worker in the given period.
     *
     * @param workerId      the worker
     * @param employmentId  the employment context
     * @param grossPay      gross pay amount (some tax tables are tiered on gross)
     * @param periodStart   period start
     * @param periodEnd     period end
     * @return effective tax rate (0.0 - 1.0) for the period
     */
    CompletionStage<BigDecimal> effectiveTaxRate(
            WorkerId workerId,
            EmploymentId employmentId,
            BigDecimal grossPay,
            LocalDate periodStart,
            LocalDate periodEnd
    );

    /**
     * Returns the statutory minimum wage for the employment's jurisdiction and period.
     *
     * @param employmentId the employment context (used to look up jurisdiction)
     * @param periodStart  period start date
     * @return minimum monthly gross pay
     */
    CompletionStage<BigDecimal> minimumWage(EmploymentId employmentId, LocalDate periodStart);

    /**
     * Validates whether the calculated payroll for a run is consistent with all
     * applicable statutory rules.
     *
     * @param payrollRunId  the run to validate
     * @return a list of validation messages; empty if compliant
     */
    CompletionStage<java.util.List<String>> validatePayrollRun(PayrollRunId payrollRunId);
}
