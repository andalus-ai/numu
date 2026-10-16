package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.learning.LearningProgram;
import tech.kayys.numou.workforce.domain.learning.LearningProgramId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface LearningProgramRepository extends Repository<LearningProgram, LearningProgramId> {
    CompletionStage<Optional<LearningProgram>> findByTenantAndCode(TenantId tenantId, String code);
    CompletionStage<List<LearningProgram>> findActiveByTenant(TenantId tenantId);
}
