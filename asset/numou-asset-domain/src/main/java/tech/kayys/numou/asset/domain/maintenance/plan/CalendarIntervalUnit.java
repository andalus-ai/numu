package tech.kayys.numou.asset.domain.maintenance.plan;

/** Calendar unit with true calendar arithmetic (ASSET-22 §7: MONTHS != 180 DAYS). */
public enum CalendarIntervalUnit {
    DAYS,
    WEEKS,
    MONTHS,
    YEARS
}
