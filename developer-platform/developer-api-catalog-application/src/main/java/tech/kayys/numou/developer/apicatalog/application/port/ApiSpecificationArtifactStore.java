package tech.kayys.numou.developer.apicatalog.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

/** Tenant-scoped immutable storage for OpenAPI specification bytes. */
public interface ApiSpecificationArtifactStore {
    Uni<UploadedApiSpecificationArtifact> store(TenantId tenant, String productId, String version,
                                                byte[] content, String contentType, String sha256);

    Uni<RetrievedApiSpecificationArtifact> retrieve(TenantId tenant, String artifactReference,
                                                    String expectedSha256);

    record UploadedApiSpecificationArtifact(String artifactReference, String sha256,
                                             long contentLength, String contentType) { }

    record RetrievedApiSpecificationArtifact(byte[] content, String sha256,
                                              String contentType, long contentLength) {
        public RetrievedApiSpecificationArtifact {
            content = content.clone();
        }
        @Override public byte[] content() { return content.clone(); }
    }
}
