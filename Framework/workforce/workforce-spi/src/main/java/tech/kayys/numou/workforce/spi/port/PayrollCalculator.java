package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.workforce.domain.payroll.PayrollRunId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CompletionStage;

/**
 * SPI port for executing payroll calculation logic.
 * Implementations may call external payroll engines, tax tables, or rules engines.
 * The domain never embeds calculation logic; it only captures results.
 */
public interface PayrollCalculator {

    /**
     * Calculates gross pay for the given worker/employment within a payroll run.
     *
     * @param payrollRunId  the run being calculated
     * @param workerId      the worker
     * @param employmentId  the employment context
     * @param periodStart   period start date (inclusive)
     * @param periodEnd     period end date (inclusive)
     * @return a calculation result containing gross, deductions breakdown, and net
     */
    CompletionStage<PayrollCalculationResult> calculateGrossPay(
            PayrollRunId payrollRunId,
            WorkerId workerId,
            EmploymentId employmentId,
            LocalDate periodStart,
            LocalDate periodEnd
    );

    /**
     * Calculates net pay after applying all mandatory and voluntary deductions.
     *
     * @param grossPay the gross pay amount already calculated
     * @param runId    the run context (for policy look-up)
     * @param workerId the worker
     * @param employmentId the employment context
     * @return calculation result with full deductions line-up
     */
    CompletionStage<PayrollCalculationResult> calculateNetPay(
            BigDecimal grossPay,
            PayrollRunId runId,
            WorkerId workerId,
            EmploymentId employmentId
    );

    /**
     * Immutable result object carrying gross, total deductions, and net.
     */
    record PayrollCalculationResult(
            BigDecimal grossPay,
            BigDecimal totalDeductions,
            BigDecimal netPay,
            String currency
    ) {}
}
