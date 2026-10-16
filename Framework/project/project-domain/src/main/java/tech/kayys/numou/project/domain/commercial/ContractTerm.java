package tech.kayys.syirkah.project.domain.commercial;

import java.util.Objects;

/**
 * A generic contract term identified by code, e.g. PAYMENT, SLA,
 * PENALTY, RETENTION, FORCE_MAJEURE.
 */
public record ContractTerm(
        String code,
        String description
) {

    public ContractTerm {
        Objects.requireNonNull(code, "code cannot be null");

        if (code.isBlank()) {
            throw new IllegalArgumentException(
                    "Term code cannot be blank"
            );
        }

        Objects.requireNonNull(description, "description cannot be null");

        if (description.isBlank()) {
            throw new IllegalArgumentException(
                    "Term description cannot be blank"
            );
        }
    }
}