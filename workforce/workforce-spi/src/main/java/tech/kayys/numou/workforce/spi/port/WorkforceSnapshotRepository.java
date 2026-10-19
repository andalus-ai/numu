package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.analytics.WorkforceSnapshot;
import tech.kayys.numou.workforce.domain.analytics.WorkforceSnapshotId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface WorkforceSnapshotRepository extends Repository<WorkforceSnapshot, WorkforceSnapshotId> {
    CompletionStage<Optional<WorkforceSnapshot>> findByDateAndScope(TenantId tenantId, LocalDate date, String scope);
    CompletionStage<List<WorkforceSnapshot>> findByTenant(TenantId tenantId);
}
