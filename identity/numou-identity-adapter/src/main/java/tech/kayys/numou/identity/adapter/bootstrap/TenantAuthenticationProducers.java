package tech.kayys.numou.identity.adapter.bootstrap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import tech.kayys.numou.identity.application.port.MembershipManagementPort;
import tech.kayys.numou.identity.application.port.PasswordHasher;
import tech.kayys.numou.identity.application.port.TenantDiscoveryPort;
import tech.kayys.numou.identity.application.port.TenantTokenIssuerPort;
import tech.kayys.numou.identity.application.port.UserRepository;
import tech.kayys.numou.identity.application.service.TenantSelectionLoginService;
import tech.kayys.numou.identity.application.service.TenantSelectionService;

@ApplicationScoped
public class TenantAuthenticationProducers {
    @Inject TenantDiscoveryPort discovery;
    @Inject MembershipManagementPort memberships;
    @Inject TenantTokenIssuerPort tokenIssuer;
    @Inject UserRepository users;
    @Inject PasswordHasher passwordHasher;

    @Produces
    @ApplicationScoped
    TenantSelectionService tenantSelectionService() {
        return new TenantSelectionService(discovery, memberships, tokenIssuer);
    }

    @Produces
    @ApplicationScoped
    TenantSelectionLoginService tenantSelectionLoginService() {
        return new TenantSelectionLoginService(users, passwordHasher, tokenIssuer);
    }
}
