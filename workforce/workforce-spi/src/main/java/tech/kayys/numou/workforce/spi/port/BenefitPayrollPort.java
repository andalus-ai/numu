package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.workforce.domain.benefit.Benefit;
import tech.kayys.numou.workforce.domain.benefit.WorkerBenefitEnrollment;
import tech.kayys.numou.workforce.domain.worker.WorkerId;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CompletionStage;

/**
 * SPI port for integrating benefit deductions and contributions into payroll calculation.
 * Bridges the benefit and payroll bounded contexts within workforce.
 */
public interface BenefitPayrollPort {

    /**
     * Calculates the total benefit deduction for a worker in a given period.
     *
     * @param workerId      the worker
     * @param employmentId  the employment context
     * @param periodStart   calculation period start
     * @param periodEnd     calculation period end
     * @return total benefit deduction amount in the employment's currency
     */
    CompletionStage<BigDecimal> calculateBenefitDeduction(
            WorkerId workerId,
            EmploymentId employmentId,
            LocalDate periodStart,
            LocalDate periodEnd
    );

    /**
     * Calculates the employer contribution for benefits for a worker in a given period.
     */
    CompletionStage<BigDecimal> calculateEmployerBenefitContribution(
            WorkerId workerId,
            EmploymentId employmentId,
            LocalDate periodStart,
            LocalDate periodEnd
    );
}
