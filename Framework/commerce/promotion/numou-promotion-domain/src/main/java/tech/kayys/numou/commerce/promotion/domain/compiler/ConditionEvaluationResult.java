package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import java.util.Optional;

/**
 * Result of evaluating one condition (product04.md section 10).
 *
 * <p>Carries more than a boolean so the caller can explain why a promotion
 * applied or did not apply. The {@code capability} is left {@code null} on
 * the factory helpers so a leaf condition can fill it in from its own
 * {@code capability()} method.</p>
 */
public record ConditionEvaluationResult(
        String capability,
        ConditionEvaluationStatus status,
        Optional<String> reasonCode,
        Optional<String> message
) {

    public ConditionEvaluationResult {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        // Components are already Optional; tolerate a null reference so the
        // record can be built from partial data without Optional<Optional<..>>.
        reasonCode = reasonCode == null ? Optional.empty() : reasonCode;
        message = message == null ? Optional.empty() : message;
    }

    public static ConditionEvaluationResult passed(String reasonCode) {
        return new ConditionEvaluationResult(
                null,
                ConditionEvaluationStatus.PASSED,
                Optional.of(reasonCode),
                Optional.empty());
    }

    public static ConditionEvaluationResult passed(String reasonCode, String message) {
        return new ConditionEvaluationResult(
                null,
                ConditionEvaluationStatus.PASSED,
                Optional.of(reasonCode),
                Optional.of(message));
    }

    public static ConditionEvaluationResult failed(String reasonCode) {
        return new ConditionEvaluationResult(
                null,
                ConditionEvaluationStatus.FAILED,
                Optional.of(reasonCode),
                Optional.empty());
    }

    public static ConditionEvaluationResult failed(String reasonCode, String message) {
        return new ConditionEvaluationResult(
                null,
                ConditionEvaluationStatus.FAILED,
                Optional.of(reasonCode),
                Optional.of(message));
    }

    public static ConditionEvaluationResult unknown(String reasonCode) {
        return new ConditionEvaluationResult(
                null,
                ConditionEvaluationStatus.UNKNOWN,
                Optional.of(reasonCode),
                Optional.empty());
    }

    public static ConditionEvaluationResult invalid(String reasonCode) {
        return new ConditionEvaluationResult(
                null,
                ConditionEvaluationStatus.INVALID,
                Optional.of(reasonCode),
                Optional.empty());
    }

    public ConditionEvaluationResult withCapability(String capability) {
        return new ConditionEvaluationResult(capability, status, reasonCode, message);
    }
}
