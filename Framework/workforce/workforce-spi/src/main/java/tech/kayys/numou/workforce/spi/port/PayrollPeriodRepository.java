package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.payroll.PayrollPeriod;
import tech.kayys.numou.workforce.domain.payroll.PayrollPeriodId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface PayrollPeriodRepository extends Repository<PayrollPeriod, PayrollPeriodId> {

    CompletionStage<Optional<PayrollPeriod>> findByPeriod(TenantId tenantId, LocalDate start, LocalDate end);
}
