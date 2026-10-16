package tech.kayys.syirkah.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.identity.application.security.AuthorizationRequirement;
import tech.kayys.syirkah.identity.application.security.Principal;

public interface AuthorizationPort {
    Uni<Boolean> isAllowed(
            Principal principal,
            String tenantId,
            AuthorizationRequirement requirement
    );

    default Uni<Void> require(
            Principal principal,
            String tenantId,
            AuthorizationRequirement requirement
    ) {
        return isAllowed(principal, tenantId, requirement)
                .flatMap(allowed -> allowed
                        ? Uni.createFrom().voidItem()
                        : Uni.createFrom().failure(
                                new AuthorizationException(requirement.permissionName())
                        ));
    }
}
