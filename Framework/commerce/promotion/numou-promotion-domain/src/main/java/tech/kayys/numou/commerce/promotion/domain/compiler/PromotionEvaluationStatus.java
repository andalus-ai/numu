package tech.kayys.syirkah.commerce.promotion.domain.compiler;

/**
 * Outcome of evaluating one promotion (product04.md section 3).
 *
 * <p>The four-way split matters for observability, performance, debugging
 * and simulation:</p>
 * <ul>
 *   <li>{@link #APPLICABLE} -- the promotion was evaluated and its conditions
 *       passed.</li>
 *   <li>{@link #NOT_APPLICABLE} -- the promotion was evaluated and at least
 *       one condition failed.</li>
 *   <li>{@link #SKIPPED} -- the system deliberately did not evaluate the
 *       promotion because a gate already established it was not relevant.</li>
 *   <li>{@link #INVALID} -- the promotion is in a state that cannot be
 *       evaluated (e.g. a compiled fingerprint mismatch).</li>
 * </ul>
 */
public enum PromotionEvaluationStatus {
    APPLICABLE,
    NOT_APPLICABLE,
    SKIPPED,
    INVALID
}
