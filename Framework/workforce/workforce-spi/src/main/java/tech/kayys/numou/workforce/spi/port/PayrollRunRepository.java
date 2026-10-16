package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.payroll.PayrollPeriodId;
import tech.kayys.numou.workforce.domain.payroll.PayrollRun;
import tech.kayys.numou.workforce.domain.payroll.PayrollRunId;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface PayrollRunRepository extends Repository<PayrollRun, PayrollRunId> {

    CompletionStage<List<PayrollRun>> findByPeriod(PayrollPeriodId periodId);
}
