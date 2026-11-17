package tech.kayys.numou.developer.apicatalog.application.query;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.developer.apicatalog.application.port.ApiArtifactGovernanceRepository;
import tech.kayys.numou.developer.apicatalog.application.port.ApiCatalogAuthorizationPort;
import tech.kayys.numou.foundation.application.context.ExecutionContextProvider;
import tech.kayys.numou.foundation.application.result.ApplicationError;

import java.util.Objects;

public final class GetApiArtifactGovernanceQueryHandler {
    private final ApiArtifactGovernanceRepository governance;
    private final ApiCatalogAuthorizationPort authorization;
    private final ExecutionContextProvider contexts;

    public GetApiArtifactGovernanceQueryHandler(ApiArtifactGovernanceRepository governance,
            ApiCatalogAuthorizationPort authorization, ExecutionContextProvider contexts) {
        this.governance = Objects.requireNonNull(governance);
        this.authorization = Objects.requireNonNull(authorization);
        this.contexts = Objects.requireNonNull(contexts);
    }

    public Uni<ApiArtifactGovernanceRepository.ArtifactGovernanceView> handle(GetApiArtifactGovernanceQuery query) {
        var context = contexts.current();
        var tenant = context.requireTenant();
        return authorization.requireCanRead(context, tenant)
                .chain(() -> governance.find(tenant, query.productId().toString(), query.version()))
                .map(found -> found.orElseThrow(() -> ApplicationError.of("API_ARTIFACT_NOT_FOUND",
                        "API artifact does not exist in this provider tenant").toException()));
    }
}
