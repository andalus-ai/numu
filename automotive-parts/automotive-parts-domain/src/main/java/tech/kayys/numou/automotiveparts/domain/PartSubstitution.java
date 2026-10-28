package tech.kayys.numou.automotiveparts.domain;

import tech.kayys.numou.product.domain.product.ProductId;
import java.time.LocalDate;
import java.util.Objects;

/** Directed substitution recommendation; not proof of fitment or universal repair authorization. */
public record PartSubstitution(String tenantId, ProductId sourceProductId, ProductId substituteProductId,
                               SubstitutionKind kind, boolean requiresTechnicianApproval, String rationale,
                               LocalDate effectiveFrom, LocalDate effectiveThrough) {
    public PartSubstitution {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        tenantId = tenantId.trim();
        Objects.requireNonNull(sourceProductId, "sourceProductId is required");
        Objects.requireNonNull(substituteProductId, "substituteProductId is required");
        Objects.requireNonNull(kind, "kind is required");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom is required");
        if (sourceProductId.equals(substituteProductId)) throw new IllegalArgumentException("A product cannot substitute itself");
        rationale = rationale == null ? "" : rationale.trim();
        if (effectiveThrough != null && effectiveThrough.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveThrough cannot precede effectiveFrom");
        }
    }

    public boolean isEffectiveOn(LocalDate date) {
        Objects.requireNonNull(date, "date is required");
        return !date.isBefore(effectiveFrom) && (effectiveThrough == null || !date.isAfter(effectiveThrough));
    }

    public enum SubstitutionKind { APPROVED_EQUIVALENT, OEM_SUPERSESSION, CONDITIONAL, EMERGENCY_ONLY }
}