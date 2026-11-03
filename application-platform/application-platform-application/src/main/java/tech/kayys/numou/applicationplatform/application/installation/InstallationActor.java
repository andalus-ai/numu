package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.util.Objects;

/** Verified actor context supplied by the host security integration, never by request payload fields. */
public record InstallationActor(TenantId tenantId, String actorId, String correlationId) {
    public InstallationActor {
        Objects.requireNonNull(tenantId, "tenantId");
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        actorId = actorId.trim();
        correlationId = correlationId == null ? "" : correlationId.trim();
    }
}
