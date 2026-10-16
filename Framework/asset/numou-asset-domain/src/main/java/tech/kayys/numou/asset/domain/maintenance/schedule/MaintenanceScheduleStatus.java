package tech.kayys.syirkah.asset.domain.maintenance.schedule;

/** Status of a plan-to-asset binding (ASSET-22 §9). */
public enum MaintenanceScheduleStatus {
    ACTIVE,
    SUSPENDED,
    COMPLETED,
    CANCELLED
}
