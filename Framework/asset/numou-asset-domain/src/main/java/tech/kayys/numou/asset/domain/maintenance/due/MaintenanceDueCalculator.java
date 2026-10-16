package tech.kayys.numou.asset.domain.maintenance.due;

import tech.kayys.numou.asset.domain.maintenance.plan.CalendarIntervalUnit;
import tech.kayys.numou.asset.domain.maintenance.plan.MaintenanceRule;
import tech.kayys.numou.asset.domain.maintenance.schedule.MaintenanceSchedule;
import tech.kayys.numou.asset.domain.maintenance.schedule.MeterScheduleBaseline;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Pure domain service computing the next due point (ASSET-22 §§12-18).
 *
 * <p>Deterministic: all time comes from the caller-supplied {@code now}; meter state
 * arrives as explicit snapshots so missing/backdated readings are handled explicitly.</p>
 */
public final class MaintenanceDueCalculator {

    private MaintenanceDueCalculator() {}

    /** Runtime inputs for one evaluation. Null current meter = missing reading. */
    public record EvaluationContext(
            Instant now,
            BigDecimal currentMeterValue,
            Instant currentMeterRecordedAt
    ) {
        public EvaluationContext {
            Objects.requireNonNull(now, "now cannot be null");
        }
    }

    /** Computed due point for one rule. */
    public record DuePoint(
            Instant dueAt,
            BigDecimal dueMeterValue,
            MaintenanceDueStatus status,
            MaintenanceDueWindow window
    ) {}

    public static DuePoint calculate(MaintenanceSchedule schedule, MaintenanceRule rule, EvaluationContext ctx) {
        Objects.requireNonNull(schedule, "schedule cannot be null");
        Objects.requireNonNull(rule, "rule cannot be null");
        Objects.requireNonNull(ctx, "ctx cannot be null");
        Instant lastService = schedule.lastServiceAt() == null ? schedule.effectiveFrom() : schedule.lastServiceAt();
        return switch (rule.type()) {
            case CALENDAR -> calendarPoint(lastService, rule, ctx);
            case METER -> meterPoint(schedule, rule, ctx, lastService);
            case CALENDAR_OR_METER -> orPoint(schedule, rule, ctx, lastService);
            case CALENDAR_AND_METER -> andPoint(schedule, rule, ctx, lastService);
        };
    }

    private static DuePoint calendarPoint(Instant lastService, MaintenanceRule rule, EvaluationContext ctx) {
        Instant dueAt = addInterval(lastService, rule.calendarIntervalValue(), rule.calendarIntervalUnit());
        MaintenanceDueWindow window = new MaintenanceDueWindow(
                earlyDate(dueAt, rule), dueAt, lateDate(dueAt, rule), null, null, null);
        return new DuePoint(dueAt, null, classifyDate(ctx.now(), dueAt, rule), window);
    }

    private static DuePoint meterPoint(MaintenanceSchedule schedule, MaintenanceRule rule,
                                       EvaluationContext ctx, Instant lastService) {
        BigDecimal baseline = baselineFor(schedule, rule);
        // Missing reading: keep UPCOMING at the computed due value; caller persists and re-evaluates later.
        BigDecimal dueValue = baseline.add(rule.meterInterval());
        BigDecimal current = effectiveCurrent(ctx, baseline);
        MaintenanceDueWindow window = new MaintenanceDueWindow(
                null, null, null,
                earlyMeter(dueValue, rule), dueValue, lateMeter(dueValue, rule));
        // Backdated reading (older than baseline instant / below baseline) is ignored for status.
        MaintenanceDueStatus status = classifyMeter(current, dueValue, rule);
        return new DuePoint(null, dueValue, status, window);
    }

    private static DuePoint orPoint(MaintenanceSchedule schedule, MaintenanceRule rule,
                                    EvaluationContext ctx, Instant lastService) {
        DuePoint calendar = calendarPoint(lastService, rule, ctx);
        DuePoint meter = meterPoint(schedule, rule, ctx, lastService);
        // Whichever is due/overdue first wins; otherwise the nearer trigger wins.
        boolean calendarHit = calendar.status() != MaintenanceDueStatus.UPCOMING;
        boolean meterHit = meter.status() != MaintenanceDueStatus.UPCOMING;
        if (calendarHit && !meterHit) {
            return calendar;
        }
        if (meterHit && !calendarHit) {
            return withBoth(calendar, meter, meter.status());
        }
        if (calendarHit) {
            return withBoth(calendar, meter, worst(calendar.status(), meter.status()));
        }
        // Neither hit: effective trigger is the earlier one (calendar date shown, meter kept).
        return withBoth(calendar, meter, MaintenanceDueStatus.UPCOMING);
    }

    private static DuePoint andPoint(MaintenanceSchedule schedule, MaintenanceRule rule,
                                     EvaluationContext ctx, Instant lastService) {
        DuePoint calendar = calendarPoint(lastService, rule, ctx);
        DuePoint meter = meterPoint(schedule, rule, ctx, lastService);
        // Both must be reached; missing meter reading can never satisfy AND.
        boolean calendarHit = calendar.status() != MaintenanceDueStatus.UPCOMING;
        boolean meterHit = ctx.currentMeterValue() != null && meter.status() != MaintenanceDueStatus.UPCOMING;
        MaintenanceDueStatus status = (calendarHit && meterHit)
                ? worst(calendar.status(), meter.status())
                : MaintenanceDueStatus.UPCOMING;
        return withBoth(calendar, meter, status);
    }

    private static DuePoint withBoth(DuePoint calendar, DuePoint meter, MaintenanceDueStatus status) {
        MaintenanceDueWindow window = new MaintenanceDueWindow(
                calendar.window().earliestDate(), calendar.window().dueDate(), calendar.window().latestDate(),
                meter.window().earliestMeter(), meter.window().dueMeter(), meter.window().latestMeter());
        return new DuePoint(calendar.dueAt(), meter.dueMeterValue(), status, window);
    }

    private static MaintenanceDueStatus worst(MaintenanceDueStatus a, MaintenanceDueStatus b) {
        if (a == MaintenanceDueStatus.OVERDUE || b == MaintenanceDueStatus.OVERDUE) {
            return MaintenanceDueStatus.OVERDUE;
        }
        if (a == MaintenanceDueStatus.DUE || b == MaintenanceDueStatus.DUE) {
            return MaintenanceDueStatus.DUE;
        }
        return MaintenanceDueStatus.UPCOMING;
    }

    private static BigDecimal baselineFor(MaintenanceSchedule schedule, MaintenanceRule rule) {
        for (MeterScheduleBaseline baseline : schedule.baselines()) {
            if (baseline.meterType() == rule.meterType()) {
                return baseline.baselineValue();
            }
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal effectiveCurrent(EvaluationContext ctx, BigDecimal baseline) {
        if (ctx.currentMeterValue() == null) {
            return null;
        }
        // Backdated/below-baseline readings are treated as baseline (ignored for progress).
        if (ctx.currentMeterValue().compareTo(baseline) < 0) {
            return baseline;
        }
        return ctx.currentMeterValue();
    }

    private static MaintenanceDueStatus classifyDate(Instant now, Instant dueAt, MaintenanceRule rule) {
        if (!now.isBefore(dueAt)) {
            return lateDate(dueAt, rule) != null && now.isAfter(lateDate(dueAt, rule))
                    ? MaintenanceDueStatus.OVERDUE : MaintenanceDueStatus.DUE;
        }
        Instant early = earlyDate(dueAt, rule);
        if (early != null && !now.isBefore(early)) {
            return MaintenanceDueStatus.DUE;
        }
        return MaintenanceDueStatus.UPCOMING;
    }

    private static MaintenanceDueStatus classifyMeter(BigDecimal current, BigDecimal dueValue, MaintenanceRule rule) {
        if (current == null) {
            return MaintenanceDueStatus.UPCOMING;
        }
        BigDecimal early = earlyMeter(dueValue, rule);
        BigDecimal late = lateMeter(dueValue, rule);
        if (current.compareTo(dueValue) >= 0) {
            return late != null && current.compareTo(late) > 0
                    ? MaintenanceDueStatus.OVERDUE : MaintenanceDueStatus.DUE;
        }
        if (early != null && current.compareTo(early) >= 0) {
            return MaintenanceDueStatus.DUE;
        }
        return MaintenanceDueStatus.UPCOMING;
    }

    private static Instant addInterval(Instant base, int value, CalendarIntervalUnit unit) {
        var zoned = base.atZone(ZoneOffset.UTC);
        return switch (unit) {
            case DAYS -> zoned.plusDays(value).toInstant();
            case WEEKS -> zoned.plusWeeks(value).toInstant();
            case MONTHS -> zoned.plusMonths(value).toInstant();
            case YEARS -> zoned.plusYears(value).toInstant();
        };
    }

    private static Instant earlyDate(Instant dueAt, MaintenanceRule rule) {
        // Early tolerance for dates is not modelled numerically; treat as due-window entry only when set.
        return null;
    }

    private static Instant lateDate(Instant dueAt, MaintenanceRule rule) {
        if (rule.lateTolerance() == null) {
            return dueAt;
        }
        return dueAt.plusSeconds(rule.lateTolerance().longValue() * 86400L);
    }

    private static BigDecimal earlyMeter(BigDecimal dueValue, MaintenanceRule rule) {
        if (rule.earlyTolerance() == null) {
            return null;
        }
        return dueValue.subtract(rule.earlyTolerance());
    }

    private static BigDecimal lateMeter(BigDecimal dueValue, MaintenanceRule rule) {
        if (rule.lateTolerance() == null) {
            return dueValue;
        }
        return dueValue.add(rule.lateTolerance());
    }
}
