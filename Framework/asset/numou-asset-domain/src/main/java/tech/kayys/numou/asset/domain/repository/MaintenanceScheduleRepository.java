package tech.kayys.syirkah.asset.domain.repository;

import tech.kayys.syirkah.asset.domain.maintenance.schedule.MaintenanceSchedule;
import tech.kayys.syirkah.asset.domain.maintenance.schedule.MaintenanceScheduleId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Tenant-scoped outbound port for maintenance schedules (ASSET-22). */
public interface MaintenanceScheduleRepository {
    CompletionStage<Optional<MaintenanceSchedule>> findByTenantAndId(String tenantId, MaintenanceScheduleId id);
    CompletionStage<List<MaintenanceSchedule>> findActiveByAsset(String tenantId, UUID assetId);
    CompletionStage<Void> save(MaintenanceSchedule schedule);
}
