package tech.kayys.numou.developer.apicatalog.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

/** Tenant-scoped immutable storage for OpenAPI specification bytes. */
public interface ApiSpecificationArtifactStore {
    Uni<UploadedApiSpecificationArtifact> store(TenantId tenant, String productId, String version,
                                                byte[] content, String contentType, String sha256);

    record UploadedApiSpecificationArtifact(String artifactReference, String sha256,
                                             long contentLength, String contentType) { }
}
