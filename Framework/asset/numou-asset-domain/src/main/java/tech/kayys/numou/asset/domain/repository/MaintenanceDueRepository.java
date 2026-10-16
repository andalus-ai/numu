package tech.kayys.syirkah.asset.domain.repository;

import tech.kayys.syirkah.asset.domain.maintenance.due.MaintenanceDue;
import tech.kayys.syirkah.asset.domain.maintenance.due.MaintenanceDueId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Append-only outbound port for due occurrences (ASSET-22). */
public interface MaintenanceDueRepository {
    CompletionStage<Optional<MaintenanceDue>> findByTenantAndId(String tenantId, MaintenanceDueId id);
    CompletionStage<List<MaintenanceDue>> findOpenByScheduleAndRule(String tenantId, UUID scheduleId, UUID ruleId);
    CompletionStage<List<MaintenanceDue>> findByAsset(String tenantId, UUID assetId);
    CompletionStage<Void> save(MaintenanceDue due);
}
