package tech.kayys.numou.workforce.domain.development;

/**
 * Priority level assigned to a {@link DevelopmentNeed}.
 */
public enum DevelopmentNeedPriority {
    /** Must be addressed urgently. */
    HIGH,
    /** Should be addressed in the near term. */
    MEDIUM,
    /** Can be addressed when resources permit. */
    LOW
}
