package tech.kayys.numou.identity.adapter.bootstrap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.identity.application.command.ChangeUserEmailCommandHandler;
import tech.kayys.numou.identity.application.command.DeactivateUserCommandHandler;
import tech.kayys.numou.identity.application.command.RegisterUserCommandHandler;
import tech.kayys.numou.identity.application.port.AuthorizationPort;
import tech.kayys.numou.identity.application.port.CurrentPrincipalPort;
import tech.kayys.numou.identity.application.port.IdentityOutboxPort;
import tech.kayys.numou.identity.application.port.MembershipManagementPort;
import tech.kayys.numou.identity.application.port.PasswordHasher;
import tech.kayys.numou.identity.application.port.RoleAssignmentPort;
import tech.kayys.numou.identity.application.port.RoleRepository;
import tech.kayys.numou.identity.application.port.TenantMembershipPort;
import tech.kayys.numou.identity.application.port.UserRepository;
import tech.kayys.numou.identity.application.query.GetUserByIdQueryHandler;
import tech.kayys.numou.identity.application.query.ListUsersQueryHandler;
import tech.kayys.numou.identity.application.service.AuthorizationService;
import tech.kayys.numou.identity.application.service.RoleManagementService;

/**
 * Composition root for the Identity service. Handlers are plain
 * classes with constructor-injected ports (no CDI annotations on the
 * application layer itself, so it stays framework-free) - this is
 * the one place that wires ports (implemented in this module) into
 * use cases (defined in numou-identity-application).
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
