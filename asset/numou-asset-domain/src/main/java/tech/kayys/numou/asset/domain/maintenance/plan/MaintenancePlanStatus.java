package tech.kayys.numou.asset.domain.maintenance.plan;

/** Lifecycle status of a maintenance plan (ASSET-22 §4). */
public enum MaintenancePlanStatus {
    DRAFT,
    ACTIVE,
    SUSPENDED,
    RETIRED
}
