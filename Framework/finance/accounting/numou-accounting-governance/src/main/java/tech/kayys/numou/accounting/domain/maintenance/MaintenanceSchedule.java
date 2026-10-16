package tech.kayys.syirkah.accounting.domain.maintenance;

import java.util.Objects;

public record MaintenanceSchedule(
        String scheduleId,
        String assetId,
        String maintenanceType,
        int intervalDays,
        boolean active
) {
    public MaintenanceSchedule {
        Objects.requireNonNull(scheduleId, "scheduleId must not be null");
        Objects.requireNonNull(assetId, "assetId must not be null");
        Objects.requireNonNull(maintenanceType, "maintenanceType must not be null");
        if (intervalDays <= 0) throw new IllegalArgumentException("intervalDays must be positive");
    }
    public String taskDescription() { return maintenanceType; }
}
