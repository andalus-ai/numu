package tech.kayys.numou.developer.apicatalog.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.developer.apicatalog.application.port.ApiArtifactGovernanceRepository;
import tech.kayys.numou.developer.apicatalog.application.port.ApiCatalogAuthorizationPort;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.context.ExecutionContextProvider;
import tech.kayys.numou.foundation.application.result.ApplicationError;

import java.util.Objects;

/** Applies or releases an artifact legal hold using trusted tenant and actor context. */
public final class SetApiArtifactLegalHoldCommandHandler
        implements CommandHandler<SetApiArtifactLegalHoldCommand, Void> {
    private final ApiArtifactGovernanceRepository governance;
    private final ApiCatalogAuthorizationPort authorization;
    private final ExecutionContextProvider contexts;

    public SetApiArtifactLegalHoldCommandHandler(ApiArtifactGovernanceRepository governance,
            ApiCatalogAuthorizationPort authorization, ExecutionContextProvider contexts) {
        this.governance = Objects.requireNonNull(governance);
        this.authorization = Objects.requireNonNull(authorization);
        this.contexts = Objects.requireNonNull(contexts);
    }

    @Override
    public Uni<Void> handle(SetApiArtifactLegalHoldCommand command) {
        var context = contexts.current();
        var tenant = context.requireTenant();
        String actor = context.actorId().value();
        String reason = validateReason(command.reason());
        if ("anonymous".equals(actor)) {
            return Uni.createFrom().failure(ApplicationError.of("API_CATALOG_ACTOR_REQUIRED",
                    "A verified actor is required for artifact governance").toException());
        }
        return authorization.requireCanManage(context, tenant)
                .chain(() -> governance.setLegalHold(tenant, command.productId().toString(),
                        command.version(), command.held(), actor, reason, context.now()))
                .replaceWithVoid();
    }

    private static String validateReason(String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw ApplicationError.of("API_ARTIFACT_GOVERNANCE_REASON_REQUIRED",
                    "A reason between 1 and 500 characters is required").toException();
        }
        return reason.trim();
    }
}
