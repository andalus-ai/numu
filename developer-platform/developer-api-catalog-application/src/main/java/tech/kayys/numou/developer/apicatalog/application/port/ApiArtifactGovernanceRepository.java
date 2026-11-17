package tech.kayys.numou.developer.apicatalog.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;

/** Tenant-scoped governance operations for immutable API specification artifacts. */
public interface ApiArtifactGovernanceRepository {
    /** Returns true only when the hold state changed. */
    Uni<Boolean> setLegalHold(TenantId tenant, String productId, String version, boolean held,
                              String actorReference, String reason, Instant occurredAt);

    /** Extends retention; shortening or clearing an existing retention deadline is forbidden. */
    Uni<Boolean> extendRetention(TenantId tenant, String productId, String version, Instant retentionUntil,
                                 String actorReference, String reason, Instant occurredAt);
}
