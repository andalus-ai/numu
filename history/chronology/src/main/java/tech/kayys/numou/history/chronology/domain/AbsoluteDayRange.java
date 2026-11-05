package tech.kayys.numou.history.chronology.domain;

/** Inclusive bounds on the chronology engine's absolute-day axis. */
public record AbsoluteDayRange(long firstDay, long lastDay) {
    public AbsoluteDayRange {
        if (firstDay > lastDay) {
            throw new IllegalArgumentException("First day must not be after last day");
        }
    }

    public boolean isSingleDay() {
        return firstDay == lastDay;
    }
}
