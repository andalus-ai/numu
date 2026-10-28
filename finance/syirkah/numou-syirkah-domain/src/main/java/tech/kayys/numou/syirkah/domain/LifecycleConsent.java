package tech.kayys.numou.numoudomain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One partner's recorded answer to a proposed change. {@code evidence} is a reference to the signed record
 * (document id, signature id). An explicit {@code approved = false} is a dissent and is kept, not dropped.
 */
public record LifecycleConsent(PartnerId partner, boolean approved, LocalDate decidedOn, String evidence) {
    public LifecycleConsent {
        Objects.requireNonNull(partner, "partner");
        Objects.requireNonNull(decidedOn, "decidedOn");
        if (evidence == null || evidence.isBlank()) {
            throw new IllegalArgumentException("consent evidence is required");
        }
    }
}
