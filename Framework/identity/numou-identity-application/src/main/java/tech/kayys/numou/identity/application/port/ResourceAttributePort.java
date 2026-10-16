package tech.kayys.numou.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.identity.application.security.Principal;
import tech.kayys.numou.identity.application.security.AuthorizationRequirement;
import tech.kayys.numou.identity.application.security.abac.AuthorizationContext;

public interface ResourceAttributePort {
    Uni<AuthorizationContext> resolve(
            Principal principal,
            String tenantId,
            AuthorizationRequirement requirement,
            String resourceId
    );
}
