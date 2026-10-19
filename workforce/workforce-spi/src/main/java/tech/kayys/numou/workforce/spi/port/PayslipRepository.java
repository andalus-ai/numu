package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.payroll.PayrollRunId;
import tech.kayys.numou.workforce.domain.payslip.Payslip;
import tech.kayys.numou.workforce.domain.payslip.PayslipId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface PayslipRepository extends Repository<Payslip, PayslipId> {

    CompletionStage<Optional<Payslip>> findByEmploymentAndRun(EmploymentId employmentId, PayrollRunId payrollRunId);

    CompletionStage<List<Payslip>> findByRun(PayrollRunId payrollRunId);
}
