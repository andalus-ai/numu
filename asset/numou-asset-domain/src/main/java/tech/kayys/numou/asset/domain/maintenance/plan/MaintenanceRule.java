package tech.kayys.numou.asset.domain.maintenance.plan;

import tech.kayys.numou.asset.domain.meter.MeterType;
import tech.kayys.numou.asset.domain.meter.MeterUnit;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * When-maintenance-is-due rule owned by a {@link MaintenancePlan} (ASSET-22 §5-§8).
 *
 * <p>References a meter <em>type</em> (e.g. ODOMETER), never a meter id or a live
 * reading: runtime state arrives via the due evaluation context.</p>
 */
public final class MaintenanceRule {

    private final UUID id;
    private final MaintenanceRuleType type;
    private final Integer calendarIntervalValue;
    private final CalendarIntervalUnit calendarIntervalUnit;
    private final MeterType meterType;
    private final BigDecimal meterInterval;
    private final MeterUnit meterUnit;
    private final BigDecimal earlyTolerance;
    private final BigDecimal lateTolerance;
    private boolean active = true;

    private MaintenanceRule(UUID id, MaintenanceRuleType type, Integer calendarIntervalValue,
                            CalendarIntervalUnit calendarIntervalUnit, MeterType meterType,
                            BigDecimal meterInterval, MeterUnit meterUnit,
                            BigDecimal earlyTolerance, BigDecimal lateTolerance) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.calendarIntervalValue = calendarIntervalValue;
        this.calendarIntervalUnit = calendarIntervalUnit;
        this.meterType = meterType;
        this.meterInterval = meterInterval;
        this.meterUnit = meterUnit;
        this.earlyTolerance = earlyTolerance;
        this.lateTolerance = lateTolerance;
    }

    public static MaintenanceRule calendar(UUID id, int intervalValue, CalendarIntervalUnit unit) {
        return of(id, MaintenanceRuleType.CALENDAR, intervalValue, unit, null, null, null, null, null);
    }

    public static MaintenanceRule meter(UUID id, MeterType meterType, BigDecimal interval, MeterUnit unit) {
        return of(id, MaintenanceRuleType.METER, null, null, meterType, interval, unit, null, null);
    }

    public static MaintenanceRule combined(UUID id, MaintenanceRuleType type, int calendarValue,
                                           CalendarIntervalUnit calendarUnit, MeterType meterType,
                                           BigDecimal meterInterval, MeterUnit meterUnit) {
        return of(id, type, calendarValue, calendarUnit, meterType, meterInterval, meterUnit, null, null);
    }

    public static MaintenanceRule of(UUID id, MaintenanceRuleType type, Integer calendarValue,
                                     CalendarIntervalUnit calendarUnit, MeterType meterType,
                                     BigDecimal meterInterval, MeterUnit meterUnit,
                                     BigDecimal earlyTolerance, BigDecimal lateTolerance) {
        Objects.requireNonNull(type, "type cannot be null");
        boolean needsCalendar = type == MaintenanceRuleType.CALENDAR
                || type == MaintenanceRuleType.CALENDAR_OR_METER
                || type == MaintenanceRuleType.CALENDAR_AND_METER;
        boolean needsMeter = type == MaintenanceRuleType.METER
                || type == MaintenanceRuleType.CALENDAR_OR_METER
                || type == MaintenanceRuleType.CALENDAR_AND_METER;
        if (needsCalendar) {
            if (calendarValue == null || calendarValue <= 0) {
                throw new BusinessRuleViolation("calendar interval must be positive for " + type);
            }
            if (calendarUnit == null) {
                throw new BusinessRuleViolation("calendar interval unit is required for " + type);
            }
        }
        if (needsMeter) {
            if (meterType == null) {
                throw new BusinessRuleViolation("meter type is required for " + type);
            }
            if (meterInterval == null || meterInterval.signum() <= 0) {
                throw new BusinessRuleViolation("meter interval must be positive for " + type);
            }
            if (meterUnit == null) {
                throw new BusinessRuleViolation("meter unit is required for " + type);
            }
        }
        if (earlyTolerance != null && earlyTolerance.signum() < 0) {
            throw new BusinessRuleViolation("early tolerance cannot be negative");
        }
        if (lateTolerance != null && lateTolerance.signum() < 0) {
            throw new BusinessRuleViolation("late tolerance cannot be negative");
        }
        return new MaintenanceRule(id == null ? UUID.randomUUID() : id, type, calendarValue,
                calendarUnit, meterType, meterInterval, meterUnit, earlyTolerance, lateTolerance);
    }

    public void deactivate() { this.active = false; }
    public void reactivate() { this.active = true; }

    public UUID id() { return id; }
    public MaintenanceRuleType type() { return type; }
    public Integer calendarIntervalValue() { return calendarIntervalValue; }
    public CalendarIntervalUnit calendarIntervalUnit() { return calendarIntervalUnit; }
    public MeterType meterType() { return meterType; }
    public BigDecimal meterInterval() { return meterInterval; }
    public MeterUnit meterUnit() { return meterUnit; }
    public BigDecimal earlyTolerance() { return earlyTolerance; }
    public BigDecimal lateTolerance() { return lateTolerance; }
    public boolean active() { return active; }
}
