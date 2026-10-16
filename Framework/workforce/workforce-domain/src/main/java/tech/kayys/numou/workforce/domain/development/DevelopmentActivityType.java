package tech.kayys.numou.workforce.domain.development;

/**
 * Categorises the type of a {@link DevelopmentActivity}.
 */
public enum DevelopmentActivityType {
    /** Formal or informal training programme. */
    TRAINING,
    /** One-to-one mentoring with an experienced colleague. */
    MENTORING,
    /** Structured coaching engagement. */
    COACHING,
    /** On-the-job project assignment for learning. */
    PROJECT,
    /** Self-directed reading / study. */
    READING,
    /** Job-shadowing another role or team. */
    SHADOWING,
    /** Attendance at an industry conference or seminar. */
    CONFERENCE,
    /** Pursuing a professional certification. */
    CERTIFICATION,
    /** Any other activity type. */
    OTHER
}
