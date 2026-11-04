package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.util.Objects;

/** Verified actor context supplied by the host security integration, never by request payload fields. */
public record InstallationActor(TenantId tenantId, String actorId, String correlationId) {
    public InstallationActor {
        Objects.requireNonNull(tenantId, "tenantId");
        if (actorId == null || actorId.isBlank() || actorId.trim().length() > 200)
            throw new IllegalArgumentException("actorId must contain 1 to 200 characters");
        actorId = actorId.trim();
        correlationId = correlationId == null ? "" : correlationId.trim();
        if (correlationId.length() > 200) throw new IllegalArgumentException("correlationId must not exceed 200 characters");
    }
}
