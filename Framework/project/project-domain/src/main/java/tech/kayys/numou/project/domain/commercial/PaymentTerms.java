package tech.kayys.syirkah.project.domain.commercial;

/**
 * Typed payment terms - kept as a value object rather than an opaque
 * JSON blob so future domains can reason about them.
 */
public record PaymentTerms(
        int paymentDueDays,
        boolean advanceRequired,
        boolean retentionApplicable
) {

    public PaymentTerms {
        if (paymentDueDays < 0) {
            throw new IllegalArgumentException(
                    "paymentDueDays cannot be negative"
            );
        }
    }
}