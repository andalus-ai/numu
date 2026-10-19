package tech.kayys.numou.asset.domain.maintenance.plan;

/** When a maintenance rule becomes due (ASSET-22 §5). */
public enum MaintenanceRuleType {
    CALENDAR,
    METER,
    CALENDAR_OR_METER,
    CALENDAR_AND_METER
}
