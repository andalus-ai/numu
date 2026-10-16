package tech.kayys.syirkah.identity.adapter.bootstrap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.transaction.UnitOfWork;
import tech.kayys.syirkah.foundation.domain.time.DomainClock;
import tech.kayys.syirkah.identity.application.command.ChangeUserEmailCommandHandler;
import tech.kayys.syirkah.identity.application.command.DeactivateUserCommandHandler;
import tech.kayys.syirkah.identity.application.command.RegisterUserCommandHandler;
import tech.kayys.syirkah.identity.application.port.AuthorizationPort;
import tech.kayys.syirkah.identity.application.port.CurrentPrincipalPort;
import tech.kayys.syirkah.identity.application.port.IdentityOutboxPort;
import tech.kayys.syirkah.identity.application.port.MembershipManagementPort;
import tech.kayys.syirkah.identity.application.port.PasswordHasher;
import tech.kayys.syirkah.identity.application.port.RoleAssignmentPort;
import tech.kayys.syirkah.identity.application.port.RoleRepository;
import tech.kayys.syirkah.identity.application.port.TenantMembershipPort;
import tech.kayys.syirkah.identity.application.port.UserRepository;
import tech.kayys.syirkah.identity.application.query.GetUserByIdQueryHandler;
import tech.kayys.syirkah.identity.application.query.ListUsersQueryHandler;
import tech.kayys.syirkah.identity.application.service.AuthorizationService;
import tech.kayys.syirkah.identity.application.service.RoleManagementService;

/**
 * Composition root for the Identity service. Handlers are plain
 * classes with constructor-injected ports (no CDI annotations on the
 * application layer itself, so it stays framework-free) - this is
 * the one place that wires ports (implemented in this module) into
 * use cases (defined in syirkah-identity-application).
 */
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
                domainClock
        );
    }

    @Produces
    @ApplicationScoped
    public RegisterUserCommandHandler registerUserCommandHandler() {
        return new RegisterUserCommandHandler(
                userRepository, passwordHasher, eventPublisher, unitOfWork, domainClock
        );
    }

    @Produces
    @ApplicationScoped
    public ChangeUserEmailCommandHandler changeUserEmailCommandHandler() {
        return new ChangeUserEmailCommandHandler(
                userRepository, eventPublisher, unitOfWork, domainClock
        );
    }

    @Produces
    @ApplicationScoped
    public DeactivateUserCommandHandler deactivateUserCommandHandler() {
        return new DeactivateUserCommandHandler(
                userRepository, eventPublisher, unitOfWork, domainClock
        );
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
