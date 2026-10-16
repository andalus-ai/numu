package tech.kayys.syirkah.security.domain.authorization;

import tech.kayys.syirkah.security.domain.valueobject.Decision;

import java.util.Objects;

/**
 * The answer to an {@link AccessRequest} (base01.md §P1-17).
 *
 * <p>Denials are as explicit as allows: every decision - including why -
 * is auditable, so an enterprise customer can answer "who touched this
 * shipment, and were they allowed to?" years later.
 */
public record AccessDecision(Decision decision, String reason) {

    public AccessDecision {
        Objects.requireNonNull(decision, "decision cannot be null");
        reason = reason == null ? "" : reason;
    }

    public static AccessDecision allow(String reason) {
        return new AccessDecision(Decision.ALLOW, reason);
    }

    public static AccessDecision deny(String reason) {
        return new AccessDecision(Decision.DENY, reason);
    }

    public boolean isAllowed() {
        return decision == Decision.ALLOW;
    }

    public boolean allowed() {
        return isAllowed();
    }
}
