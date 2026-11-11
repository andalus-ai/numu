package tech.kayys.numou.developer.apicatalog.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

/** Deletes one immutable API artifact after validating its tenant-scoped storage identity. */
public interface ApiArtifactObjectDeletionPort {
    /** Idempotent: an already absent object is a successful deletion. */
    Uni<Void> delete(TenantId tenant, String artifactReference, String expectedSha256);
}
