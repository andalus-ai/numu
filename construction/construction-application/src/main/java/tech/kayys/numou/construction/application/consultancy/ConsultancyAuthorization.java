package tech.kayys.numou.construction.application.consultancy;

import io.smallrye.mutiny.Uni;

import java.util.Objects;
import java.util.UUID;

/**
 * Application authorization port. Implementations must derive the actor's
 * effective permissions from trusted identity/membership data, not from
 * command-supplied organization IDs alone.
 */
@FunctionalInterface
public interface ConsultancyAuthorization {
    Uni<Void> require(AuthorizationRequest request);

    record AuthorizationRequest(
            String tenantId,
            UUID actorId,
            UUID organizationId,
            Action action) {
        public AuthorizationRequest {
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("Tenant id is required");
            }
            Objects.requireNonNull(actorId, "Actor id is required");
            Objects.requireNonNull(organizationId, "Organization id is required");
            Objects.requireNonNull(action, "Authorization action is required");
            tenantId = tenantId.trim();
        }
    }

    enum Action {
        CREATE_ENGAGEMENT,
        PROPOSE_ENGAGEMENT,
        ACTIVATE_ENGAGEMENT,
        COMPLETE_ENGAGEMENT,
        CANCEL_ENGAGEMENT,
        CREATE_DELIVERABLE,
        SUBMIT_DELIVERABLE,
        REVISE_DELIVERABLE,
        REVIEW_DELIVERABLE,
        VIEW_DELIVERABLE_HISTORY
    }
}
