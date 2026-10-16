package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.development.CareerPath;
import tech.kayys.numou.workforce.domain.development.CareerPathId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface CareerPathRepository extends Repository<CareerPath, CareerPathId> {
    CompletionStage<Optional<CareerPath>> findByTenantAndCode(TenantId tenantId, String code);
    CompletionStage<List<CareerPath>> findActiveByTenant(TenantId tenantId);
}
