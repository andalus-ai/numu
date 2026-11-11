package tech.kayys.numou.feedback.domain;

/** Stable broad categories; tenant-specific categories belong in configuration, not this enum. */
public enum FeedbackType {
    COMMENT,
    BUG,
    IDEA,
    FEATURE_REQUEST,
    ERROR_REPORT,
    RATING,
    REVIEW,
    COMPLAINT,
    SUGGESTION,
    QUESTION,
    OTHER
}