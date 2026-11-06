package tech.kayys.numou.developer.apicatalog.application.query;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.developer.apicatalog.application.port.ApiCatalogAuthorizationPort;
import tech.kayys.numou.developer.apicatalog.application.port.ApiProductRepository;
import tech.kayys.numou.developer.apicatalog.application.port.ApiSpecificationArtifactStore;
import tech.kayys.numou.developer.apicatalog.domain.ApiProductId;
import tech.kayys.numou.foundation.application.context.ExecutionContextProvider;
import tech.kayys.numou.foundation.application.query.QueryHandler;
import tech.kayys.numou.foundation.application.result.ApplicationError;

import java.util.Objects;

/** Authorizes the provider tenant and resolves the artifact only through persisted catalog metadata. */
public final class GetApiSpecificationArtifactQueryHandler implements
        QueryHandler<GetApiSpecificationArtifactQuery, ApiSpecificationArtifactStore.RetrievedApiSpecificationArtifact> {
    private final ApiProductRepository products;
    private final ApiCatalogAuthorizationPort authorization;
    private final ExecutionContextProvider contexts;
    private final ApiSpecificationArtifactStore artifacts;

    public GetApiSpecificationArtifactQueryHandler(ApiProductRepository products,
                                                   ApiCatalogAuthorizationPort authorization,
                                                   ExecutionContextProvider contexts,
                                                   ApiSpecificationArtifactStore artifacts) {
        this.products = Objects.requireNonNull(products);
        this.authorization = Objects.requireNonNull(authorization);
        this.contexts = Objects.requireNonNull(contexts);
        this.artifacts = Objects.requireNonNull(artifacts);
    }

    @Override
    public Uni<ApiSpecificationArtifactStore.RetrievedApiSpecificationArtifact> handle(
            GetApiSpecificationArtifactQuery query) {
        var context = contexts.current();
        var tenant = context.requireTenant();
        return authorization.requireCanRead(context, tenant)
                .chain(() -> products.findById(tenant, ApiProductId.of(query.productId())))
                .chain(found -> {
                    var product = found.orElseThrow(() -> ApplicationError.of(
                            "API_PRODUCT_NOT_FOUND",
                            "API product does not exist in this provider tenant").toException());
                    var version = product.versions().get(query.version());
                    if (version == null) {
                        return Uni.createFrom().failure(ApplicationError.of(
                                "API_VERSION_NOT_FOUND",
                                "API version does not exist in this provider tenant").toException());
                    }
                    var specification = version.specification();
                    return artifacts.retrieve(tenant, specification.artifactReference(), specification.sha256());
                });
    }
}
