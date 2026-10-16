package tech.kayys.syirkah.project.domain.commercial;

import java.util.Objects;

/**
 * Typed billing terms for a contract.
 */
public record BillingTerms(
        BillingTermType type,
        boolean requiresAcceptance
) {

    public BillingTerms {
        Objects.requireNonNull(type, "type cannot be null");
    }
}