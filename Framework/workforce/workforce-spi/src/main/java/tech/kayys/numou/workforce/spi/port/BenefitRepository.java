package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.benefit.Benefit;
import tech.kayys.numou.workforce.domain.benefit.BenefitId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface BenefitRepository extends Repository<Benefit, BenefitId> {

    CompletionStage<Optional<Benefit>> findByCode(TenantId tenantId, String code);

    CompletionStage<List<Benefit>> findByTenant(TenantId tenantId);
}
