package tech.kayys.numou.affiliate.application;

import java.util.Objects;

/**
 * Trusted platform-wide operations context.
 * The host must construct this value only after authenticating the actor and
 * authorizing access to cross-tenant operational diagnostics.
 */
public record AffiliatePlatformOperationsContext(String actorId) {
    public AffiliatePlatformOperationsContext {
        Objects.requireNonNull(actorId, "actorId is required");
        actorId = actorId.trim();
        if (actorId.isBlank()) {
            throw new IllegalArgumentException("actorId cannot be blank");
        }
    }
}
