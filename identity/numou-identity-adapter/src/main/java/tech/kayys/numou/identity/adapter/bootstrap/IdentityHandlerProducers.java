package tech.kayys.numou.identity.adapter.bootstrap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import tech.kayys.numou.cache.Cache;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.identity.application.command.ChangeUserEmailCommandHandler;
import tech.kayys.numou.identity.application.command.DeactivateUserCommandHandler;
import tech.kayys.numou.identity.application.command.RegisterUserCommandHandler;
import tech.kayys.numou.identity.application.dto.PatResponse;
import tech.kayys.numou.identity.application.port.AuthorizationPort;
import tech.kayys.numou.identity.application.port.CurrentPrincipalPort;
import tech.kayys.numou.identity.application.port.FederatedIdentityProviderPort;
import tech.kayys.numou.identity.application.port.FederatedIdentityRepository;
import tech.kayys.numou.identity.application.port.FederationSettingsPort;
import tech.kayys.numou.identity.application.port.IdentityOutboxPort;
import tech.kayys.numou.identity.application.port.IdentityProviderConfigRepository;
import tech.kayys.numou.identity.application.port.MembershipManagementPort;
import tech.kayys.numou.identity.application.port.PasswordHasher;
import tech.kayys.numou.identity.application.port.PersonalAccessTokenRepository;
import tech.kayys.numou.identity.application.port.RoleAssignmentPort;
import tech.kayys.numou.identity.application.port.RoleRepository;
import tech.kayys.numou.identity.application.port.ScopedRoleAssignmentRepository;
import tech.kayys.numou.identity.application.port.ServiceAccountRepository;
import tech.kayys.numou.identity.application.port.TenantMembershipPort;
import tech.kayys.numou.identity.application.port.TokenIssuerPort;
import tech.kayys.numou.identity.application.port.TokenVerifierPort;
import tech.kayys.numou.identity.application.port.UserRepository;
import tech.kayys.numou.identity.application.query.GetUserByIdQueryHandler;
import tech.kayys.numou.identity.application.query.ListUsersQueryHandler;
import tech.kayys.numou.identity.application.security.AuthorizationDecisionService;
import tech.kayys.numou.identity.application.service.AuthorizationService;
import tech.kayys.numou.identity.application.service.DirectoryAuthenticationService;
import tech.kayys.numou.identity.application.service.FederatedAuthenticationService;
import tech.kayys.numou.identity.application.service.PersonalAccessTokenService;
import tech.kayys.numou.identity.application.service.RoleManagementService;
import tech.kayys.numou.identity.application.service.SamlAuthenticationService;
import tech.kayys.numou.identity.application.service.ScimProvisioningService;
import tech.kayys.numou.identity.application.service.ScopedRoleAssignmentService;
import tech.kayys.numou.identity.application.service.ServiceAccountService;
import tech.kayys.numou.identity.application.service.TokenService;
import tech.kayys.numou.identity.adapter.outbound.idp.ldap.LdapDirectoryAdapter;
import tech.kayys.numou.identity.adapter.outbound.idp.saml.SamlAssertionAdapter;

import java.util.Set;

@ApplicationScoped
public class IdentityHandlerProducers {

    @Inject
    UserRepository userRepository;

    @Inject
    PasswordHasher passwordHasher;

    @Inject
    EventPublisher eventPublisher;

    @Inject
    UnitOfWork unitOfWork;

    @Inject
    DomainClock domainClock;

    @Inject
    CurrentPrincipalPort currentPrincipal;

    @Inject
    TenantMembershipPort tenantMembership;

    @Inject
    AuthorizationPort authorizationPort;

    @Inject
    RoleRepository roleRepository;

    @Inject
    MembershipManagementPort membershipManagement;

    @Inject
    RoleAssignmentPort roleAssignment;

    @Inject
    IdentityOutboxPort identityOutbox;

    @Inject
    FederationSettingsPort federationSettings;

    @Inject
    IdentityProviderConfigRepository identityProviderConfigs;

    @Inject
    FederatedIdentityProviderPort federatedIdentityProvider;

    @Inject
    FederatedIdentityRepository federatedIdentityRepository;

    @Inject
    Cache<java.util.Set<String>> effectivePermissionCache;

    @Inject
    Cache<PatResponse> patValidationCache;

    @Inject
    PersonalAccessTokenRepository personalAccessTokenRepository;

    @Inject
    ServiceAccountRepository serviceAccountRepository;

    @Inject
    ScopedRoleAssignmentRepository scopedRoleAssignmentRepository;

    @Inject
    TokenIssuerPort tokenIssuer;

    @Inject
    TokenVerifierPort tokenVerifier;

    @Produces
    @ApplicationScoped
    public AuthorizationService authorizationService() {
        return new AuthorizationService(currentPrincipal, tenantMembership, authorizationPort);
    }

    @Produces
    @ApplicationScoped
    public RoleManagementService roleManagementService() {
        return new RoleManagementService(
                authorizationService(),
                currentPrincipal,
                roleRepository,
                membershipManagement,
                roleAssignment,
                identityOutbox,
                unitOfWork,
                domainClock);
    }

    @Produces
    @ApplicationScoped
    public RegisterUserCommandHandler registerUserCommandHandler() {
        return new RegisterUserCommandHandler(
                userRepository, passwordHasher, eventPublisher, unitOfWork, domainClock);
    }

    @Produces
    @ApplicationScoped
    public PersonalAccessTokenService personalAccessTokenService() {
        return new PersonalAccessTokenService(
                personalAccessTokenRepository, identityOutbox, unitOfWork,
                domainClock, patValidationCache);
    }

    @Produces
    @ApplicationScoped
    public ServiceAccountService serviceAccountService() {
        return new ServiceAccountService(
                serviceAccountRepository, identityOutbox, unitOfWork, domainClock);
    }

    @Produces
    @ApplicationScoped
    public ScopedRoleAssignmentService scopedRoleAssignmentService() {
        return new ScopedRoleAssignmentService(
                scopedRoleAssignmentRepository, identityOutbox, unitOfWork,
                domainClock, effectivePermissionCache);
    }

    @Produces
    @ApplicationScoped
    public FederatedAuthenticationService federatedAuthenticationService() {
        return new FederatedAuthenticationService(
                identityProviderConfigs, federatedIdentityProvider, federationSettings,
                federatedIdentityRepository, userRepository, scopedRoleAssignmentService(),
                passwordHasher, eventPublisher, tokenService(), unitOfWork, domainClock);
    }

    @Produces
    @ApplicationScoped
    public LdapDirectoryAdapter ldapDirectoryAdapter() {
        return new LdapDirectoryAdapter(federationSettings);
    }

    @Produces
    @ApplicationScoped
    public DirectoryAuthenticationService directoryAuthenticationService() {
        return new DirectoryAuthenticationService(federatedAuthenticationService(), ldapDirectoryAdapter());
    }

    @Produces
    @ApplicationScoped
    public SamlAssertionAdapter samlAssertionAdapter() {
        return new SamlAssertionAdapter(federationSettings);
    }

    @Produces
    @ApplicationScoped
    public SamlAuthenticationService samlAuthenticationService() {
        return new SamlAuthenticationService(federatedAuthenticationService(), samlAssertionAdapter());
    }

    @Produces
    @ApplicationScoped
    public ScimProvisioningService scimProvisioningService() {
        return new ScimProvisioningService(
                identityProviderConfigs,
                federationSettings,
                federatedIdentityRepository,
                userRepository,
                scopedRoleAssignmentService(),
                scopedRoleAssignmentRepository,
                federatedAuthenticationService(),
                passwordHasher,
                eventPublisher,
                unitOfWork,
                domainClock);
    }

    @Produces
    @ApplicationScoped
    public TokenService tokenService() {
        return new TokenService(
                userRepository, passwordHasher, tokenIssuer, tokenVerifier, 900,
                personalAccessTokenService());
    }

    @Produces
    @ApplicationScoped
    public AuthorizationDecisionService authorizationDecisionService() {
        return new AuthorizationDecisionService();
    }

    @Produces
    @ApplicationScoped
    public ChangeUserEmailCommandHandler changeUserEmailCommandHandler() {
        return new ChangeUserEmailCommandHandler(
                userRepository, eventPublisher, unitOfWork, domainClock);
    }

    @Produces
    @ApplicationScoped
    public DeactivateUserCommandHandler deactivateUserCommandHandler() {
        return new DeactivateUserCommandHandler(
                userRepository, eventPublisher, unitOfWork, domainClock);
    }

    @Produces
    @ApplicationScoped
    public GetUserByIdQueryHandler getUserByIdQueryHandler() {
        return new GetUserByIdQueryHandler(userRepository);
    }

    @Produces
    @ApplicationScoped
    public ListUsersQueryHandler listUsersQueryHandler() {
        return new ListUsersQueryHandler(userRepository);
    }
}
