package tech.kayys.syirkah.identity.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.transaction.UnitOfWork;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.foundation.domain.time.DomainClock;
import tech.kayys.syirkah.identity.application.port.IdentityOutboxPort;
import tech.kayys.syirkah.identity.application.port.MembershipManagementPort;
import tech.kayys.syirkah.identity.application.port.RoleAssignmentPort;
import tech.kayys.syirkah.identity.application.port.RoleRepository;
import tech.kayys.syirkah.identity.application.security.AuthorizationRequirement;
import tech.kayys.syirkah.identity.application.security.PrincipalType;
import tech.kayys.syirkah.identity.application.port.CurrentPrincipalPort;
import tech.kayys.syirkah.identity.domain.role.Permission;
import tech.kayys.syirkah.identity.domain.role.Role;
import tech.kayys.syirkah.identity.domain.role.RoleId;
import tech.kayys.syirkah.identity.domain.user.UserId;

import java.util.Objects;

public final class RoleManagementService {
    private final AuthorizationService authorization;
    private final CurrentPrincipalPort currentPrincipal;
    private final RoleRepository roles;
    private final MembershipManagementPort memberships;
    private final RoleAssignmentPort assignments;
    private final IdentityOutboxPort outbox;
    private final UnitOfWork unitOfWork;
    private final DomainClock clock;

    public RoleManagementService(
            AuthorizationService authorization,
            CurrentPrincipalPort currentPrincipal,
            RoleRepository roles,
            MembershipManagementPort memberships,
            RoleAssignmentPort assignments,
            IdentityOutboxPort outbox,
            UnitOfWork unitOfWork,
            DomainClock clock
    ) {
        this.authorization = Objects.requireNonNull(authorization);
        this.currentPrincipal = Objects.requireNonNull(currentPrincipal);
        this.roles = Objects.requireNonNull(roles);
        this.memberships = Objects.requireNonNull(memberships);
        this.assignments = Objects.requireNonNull(assignments);
        this.outbox = Objects.requireNonNull(outbox);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.clock = Objects.requireNonNull(clock);
    }

    public Uni<RoleId> createRole(String tenantValue, String code, String name) {
        var tenantId = TenantId.of(tenantValue);
        var normalizedCode = requiredText(code, "code").toLowerCase(java.util.Locale.ROOT);
        var roleName = requiredText(name, "name");
        return authorization.require(
                        tenantValue, AuthorizationRequirement.permission("identity.role", "create")
                )
                .chain(() -> unitOfWork.execute(() ->
                        roles.findByCode(tenantId, normalizedCode).chain(existing -> {
                            if (existing.isPresent()) {
                                return Uni.createFrom().failure(ApplicationError.of(
                                        "ROLE_CODE_ALREADY_EXISTS", "A role with this code already exists"
                                ).toException());
                            }
                            var role = Role.create(RoleId.generate(), tenantId, normalizedCode, roleName, clock.now());
                            return roles.save(role)
                                    .chain(() -> outbox.append(tenantId, role.pullDomainEvents()))
                                    .replaceWith(role.id());
                        })
                ));
    }

    public Uni<Void> grantPermission(String tenantValue, RoleId roleId, String permissionValue) {
        var tenantId = TenantId.of(tenantValue);
        var permission = Permission.of(permissionValue);
        return authorization.require(
                        tenantValue, AuthorizationRequirement.permission("identity.role", "grant")
                )
                .chain(() -> unitOfWork.execute(() ->
                        roles.findById(tenantId, roleId).chain(optional -> {
                            if (optional.isEmpty()) {
                                return Uni.createFrom().failure(ApplicationError.of(
                                        "ROLE_NOT_FOUND", "Role does not exist in this tenant"
                                ).toException());
                            }
                            var role = optional.get();
                            role.grant(permission, clock.now());
                            return roles.save(role)
                                    .chain(() -> outbox.append(tenantId, role.pullDomainEvents()));
                        })
                ));
    }

    public Uni<Void> assignRole(String tenantValue, UserId userId, RoleId roleId) {
        var tenantId = TenantId.of(tenantValue);
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(roleId, "roleId cannot be null");
        return authorization.require(
                        tenantValue, AuthorizationRequirement.permission("identity.membership", "manage")
                )
                .chain(() -> currentPrincipal.current())
                .chain(actor -> {
                    if (actor.type() != PrincipalType.USER || actor.userId() == null) {
                        return Uni.createFrom().failure(ApplicationError.of(
                                "USER_ACTOR_REQUIRED", "Only a user may administer role assignments"
                        ).toException());
                    }
                    return unitOfWork.execute(() ->
                            memberships.isActive(tenantId, userId).chain(active -> {
                                if (!active) {
                                    return Uni.createFrom().failure(ApplicationError.of(
                                            "ACTIVE_MEMBERSHIP_REQUIRED",
                                            "Roles can only be assigned to an active tenant member"
                                    ).toException());
                                }
                                return roles.findById(tenantId, roleId).chain(role -> {
                                    if (role.isEmpty()) {
                                        return Uni.createFrom().failure(ApplicationError.of(
                                                "ROLE_NOT_FOUND", "Role does not exist in this tenant"
                                        ).toException());
                                    }
                                    return assignments.isAssigned(tenantId, userId, roleId).chain(assigned -> {
                                        if (assigned) {
                                            return Uni.createFrom().voidItem();
                                        }
                                        return assignments.assign(
                                                tenantId, userId, roleId, actor.userId(), clock.now()
                                        );
                                    });
                                });
                            })
                    );
                });
    }

    public Uni<Void> removeRole(String tenantValue, UserId userId, RoleId roleId) {
        var tenantId = TenantId.of(tenantValue);
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(roleId, "roleId cannot be null");
        return authorization.require(
                        tenantValue, AuthorizationRequirement.permission("identity.membership", "manage")
                )
                .chain(() -> currentPrincipal.current())
                .chain(actor -> {
                    if (actor.type() != PrincipalType.USER || actor.userId() == null) {
                        return Uni.createFrom().failure(ApplicationError.of(
                                "USER_ACTOR_REQUIRED", "Only a user may administer role assignments"
                        ).toException());
                    }
                    return unitOfWork.execute(() ->
                            roles.findById(tenantId, roleId).chain(role -> {
                                if (role.isEmpty()) {
                                    return Uni.createFrom().failure(ApplicationError.of(
                                            "ROLE_NOT_FOUND", "Role does not exist in this tenant"
                                    ).toException());
                                }
                                return assignments.isAssigned(tenantId, userId, roleId).chain(assigned -> {
                                    if (!assigned) {
                                        return Uni.createFrom().voidItem();
                                    }
                                    return assignments.remove(
                                            tenantId, userId, roleId, actor.userId(), clock.now()
                                    );
                                });
                            })
                    );
                });
    }

    private static String requiredText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value.trim();
    }
}
