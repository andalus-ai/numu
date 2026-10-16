package tech.kayys.syirkah.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.identity.application.security.Principal;
import tech.kayys.syirkah.identity.application.security.AuthorizationRequirement;
import tech.kayys.syirkah.identity.application.security.abac.AuthorizationContext;

public interface ResourceAttributePort {
    Uni<AuthorizationContext> resolve(
            Principal principal,
            String tenantId,
            AuthorizationRequirement requirement,
            String resourceId
    );
}
