package tech.kayys.numou.feedback.domain.rating;

/** Semantic rating measures; metric meaning is independent of presentation widgets. */
public enum RatingMetric {
    NPS(0, 10),
    CSAT(1, 5),
    CES(1, 7),
    STAR_RATING(1, 5),
    CUSTOM(null, null);

    private final Integer defaultMin;
    private final Integer defaultMax;

    RatingMetric(Integer defaultMin, Integer defaultMax) {
        this.defaultMin = defaultMin;
        this.defaultMax = defaultMax;
    }

    public Integer defaultMin() { return defaultMin; }
    public Integer defaultMax() { return defaultMax; }

    /** Inclusive top-box threshold; null means no generic favorable band is defined. */
    public Integer favorableThreshold() {
        return switch (this) {
            case NPS -> 9;
            case CSAT, STAR_RATING -> 4;
            case CES -> 5;
            case CUSTOM -> null;
        };
    }

    public boolean accepts(int min, int max) {
        if (this == CUSTOM) return min < max;
        return defaultMin != null && defaultMin == min && defaultMax == max;
    }
}