package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.workforce.domain.worker.WorkerId;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CompletionStage;

/**
 * SPI port for computing social insurance contributions (employee + employer) within payroll.
 * Adapters connect to jurisdiction-specific social insurance scheme rules.
 */
public interface SocialInsurancePayrollPort {

    /**
     * Returns the employee-side social insurance contribution for a payroll period.
     *
     * @param workerId      the worker
     * @param employmentId  the employment context
     * @param grossPay      gross pay for the period (used to compute % contributions)
     * @param periodStart   period start
     * @param periodEnd     period end
     * @return employee contribution amount (deduction from pay)
     */
    CompletionStage<BigDecimal> computeEmployeeContribution(
            WorkerId workerId,
            EmploymentId employmentId,
            BigDecimal grossPay,
            LocalDate periodStart,
            LocalDate periodEnd
    );

    /**
     * Returns the employer-side social insurance contribution for a payroll period.
     *
     * @param workerId      the worker
     * @param employmentId  the employment context
     * @param grossPay      gross pay for the period
     * @param periodStart   period start
     * @param periodEnd     period end
     * @return employer contribution amount (additional employer cost)
     */
    CompletionStage<BigDecimal> computeEmployerContribution(
            WorkerId workerId,
            EmploymentId employmentId,
            BigDecimal grossPay,
            LocalDate periodStart,
            LocalDate periodEnd
    );
}
