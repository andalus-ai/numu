package tech.kayys.syirkah.commerce.promotion.domain.compiler;

/**
 * Outcome of evaluating one condition against a controlled context
 * (product04.md section 11).
 *
 * <p>{@code UNKNOWN} is useful when the required information is not available
 * in the context. It is deliberately distinct from {@code FAILED}: missing
 * data is not the same as a condition being false. Callers decide whether
 * unknown conditions fail or skip the promotion.</p>
 */
public enum ConditionEvaluationStatus {
    PASSED,
    FAILED,
    UNKNOWN,
    INVALID
}
