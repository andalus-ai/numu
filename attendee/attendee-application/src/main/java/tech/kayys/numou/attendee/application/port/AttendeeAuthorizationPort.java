package tech.kayys.numou.attendee.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Objects;
import java.util.UUID;

/** Authorization is evaluated using trusted tenant and actor context. */
@FunctionalInterface
public interface AttendeeAuthorizationPort {

    Uni<Void> require(AuthorizationRequest request);

    record AuthorizationRequest(TenantId tenantId, UUID actorId, Action action) {
        public AuthorizationRequest {
            Objects.requireNonNull(tenantId, "tenantId cannot be null");
            Objects.requireNonNull(actorId, "actorId cannot be null");
            Objects.requireNonNull(action, "action cannot be null");
        }
    }

    enum Action {
        CREATE_PROFILE,
        VIEW_PROFILE
    }
}
