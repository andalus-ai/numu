package tech.kayys.syirkah.document.adapter.outbound.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.syirkah.document.application.port.DocumentAccessPort;
import tech.kayys.syirkah.identity.application.security.AuthorizationRequirement;
import tech.kayys.syirkah.identity.application.service.AuthorizationService;

@ApplicationScoped
public class IdentityDocumentAccessAdapter implements DocumentAccessPort {
    private final AuthorizationService authorization;

    @Inject
    public IdentityDocumentAccessAdapter(AuthorizationService authorization) {
        this.authorization = authorization;
    }

    @Override
    public io.smallrye.mutiny.Uni<Void> require(String tenantId, String permission) {
        var separator = permission == null ? -1 : permission.lastIndexOf('.');
        if (separator <= 0 || separator == permission.length() - 1) {
            return io.smallrye.mutiny.Uni.createFrom().failure(
                    new IllegalArgumentException("Permission must use resource.action format")
            );
        }
        return authorization.require(
                tenantId,
                AuthorizationRequirement.permission(permission.substring(0, separator),
                        permission.substring(separator + 1))
        );
    }
}
