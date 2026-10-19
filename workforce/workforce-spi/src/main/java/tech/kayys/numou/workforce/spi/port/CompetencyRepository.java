package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.performance.Competency;
import tech.kayys.numou.workforce.domain.performance.CompetencyId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface CompetencyRepository extends Repository<Competency, CompetencyId> {
    CompletionStage<Optional<Competency>> findByTenantAndCode(TenantId tenantId, String code);
    CompletionStage<List<Competency>> findActiveByTenant(TenantId tenantId);
}
