package tech.kayys.numou.planning.domain;
import java.util.Objects;
/** Explains gaps between approved baseline activities and the current schedule forecast. */
public record ScheduleVarianceFinding(Code code, ActivityId activityId, String detail) {
    public ScheduleVarianceFinding {
        Objects.requireNonNull(code); Objects.requireNonNull(detail);
        detail = detail.trim();
        if (detail.isEmpty()) throw new IllegalArgumentException("Finding detail cannot be blank");
    }
    public enum Code { BASELINE_ACTIVITY_MISSING_FROM_FORECAST, FORECAST_ACTIVITY_MISSING_FROM_BASELINE }
}
