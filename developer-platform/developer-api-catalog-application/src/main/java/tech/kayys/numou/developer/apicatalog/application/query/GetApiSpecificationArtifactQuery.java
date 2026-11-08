package tech.kayys.numou.developer.apicatalog.application.query;

import tech.kayys.numou.foundation.application.query.Query;
import java.util.Objects;
import java.util.UUID;

/** Retrieves the immutable OpenAPI bytes for a registered API product version. */
public record GetApiSpecificationArtifactQuery(UUID productId, String version) implements Query {
    public GetApiSpecificationArtifactQuery {
        Objects.requireNonNull(productId, "productId cannot be null");
        Objects.requireNonNull(version, "version cannot be null");
    }
}
