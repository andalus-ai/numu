package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.cache.Cache;
import tech.kayys.numou.cache.CacheKey;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.identity.application.dto.GrantRoleAssignmentRequest;
import tech.kayys.numou.identity.application.dto.RoleAssignmentResponse;
import tech.kayys.numou.identity.application.port.IdentityOutboxPort;
import tech.kayys.numou.identity.application.port.ScopedRoleAssignmentRepository;
import tech.kayys.numou.identity.domain.role.RoleId;
import tech.kayys.numou.identity.domain.role.assignment.RoleAssignment;
import tech.kayys.numou.identity.domain.role.assignment.RoleAssignmentId;
import tech.kayys.numou.identity.domain.role.assignment.RoleScopeType;
import tech.kayys.numou.identity.domain.user.UserId;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class ScopedRoleAssignmentService {

    /** L1 cache namespace for effective-permission resolutions on the hot authorization path. */
    public static final String PERMISSION_CACHE_NAMESPACE = "identity.effective-permissions";

    private final ScopedRoleAssignmentRepository roleAssignmentRepository;
    private final IdentityOutboxPort outbox;
    private final UnitOfWork unitOfWork;
    private final DomainClock clock;
    private final Cache<Set<String>> permissionCache;

    public ScopedRoleAssignmentService(
            ScopedRoleAssignmentRepository roleAssignmentRepository,
            IdentityOutboxPort outbox,
            UnitOfWork unitOfWork,
            DomainClock clock
    ) {
        this(roleAssignmentRepository, outbox, unitOfWork, clock, null);
    }

    /**
     * @param permissionCache L1 cache of effective permissions; {@code null} disables caching
     *                        (used by tests and by deployments that opt out). Keep the TTL short:
     *                        it is the upper bound on how long a revoked grant stays effective.
     */
    public ScopedRoleAssignmentService(
            ScopedRoleAssignmentRepository roleAssignmentRepository,
            IdentityOutboxPort outbox,
            UnitOfWork unitOfWork,
            DomainClock clock,
            Cache<Set<String>> permissionCache
    ) {
        this.roleAssignmentRepository = Objects.requireNonNull(roleAssignmentRepository);
        this.outbox = Objects.requireNonNull(outbox);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.clock = Objects.requireNonNull(clock);
        this.permissionCache = permissionCache;
    }

    public Uni<RoleAssignmentResponse> grant(GrantRoleAssignmentRequest request, UUID actorUserId) {
        Objects.requireNonNull(request, "request cannot be null");
        return unitOfWork.execute(() -> {
            RoleAssignmentId id = RoleAssignmentId.newId();
            RoleScopeType scopeType = RoleScopeType.valueOf(request.scopeType().toUpperCase(java.util.Locale.ROOT));
            UserId actorId = actorUserId != null ? UserId.of(actorUserId) : null;

            var assignment = RoleAssignment.grant(
                    id,
                    request.principalType(),
                    request.principalId(),
                    new RoleId(request.roleId()),
                    scopeType,
                    request.scopePath(),
                    actorId,
                    request.expiresAt(),
                    clock.now()
            );

            return roleAssignmentRepository.save(assignment)
                    .chain(() -> outbox.append(null, assignment.pullDomainEvents()))
                    // A grant/revocation must never leave a stale cached permission set behind.
                    .chain(() -> invalidatePrincipal(request.principalId()))
                    .replaceWith(RoleAssignmentResponse.from(assignment));
        });
    }

    public Uni<Void> revoke(String principalType, UUID principalId, UUID roleId, UUID actorUserId, String scopePath) {
        Objects.requireNonNull(principalType, "principalType cannot be null");
        Objects.requireNonNull(principalId, "principalId cannot be null");
        Objects.requireNonNull(roleId, "roleId cannot be null");
        Objects.requireNonNull(scopePath, "scopePath cannot be null");

        return unitOfWork.execute(() ->
                roleAssignmentRepository.deleteByPrincipalAndRole(
                        principalType, principalId, new RoleId(roleId), scopePath
                ).chain(deletedCount -> {
                    if (deletedCount == 0) {
                        return Uni.createFrom().failure(ApplicationError.of(
                                "ASSIGNMENT_NOT_FOUND", "Role assignment not found"
                        ).toException());
                    }
                    return invalidatePrincipal(principalId);
                })
        );
    }

    public Uni<List<RoleAssignmentResponse>> listForPrincipal(String principalType, UUID principalId) {
        return roleAssignmentRepository.findByPrincipal(principalType, principalId)
                .map(list -> list.stream().map(RoleAssignmentResponse::from).toList());
    }

    /**
     * Resolves effective permissions by resolving roles that cascade down from any ancestor scope,
     * then fetching the union of permission codes for those roles. Results are served from the
     * L1 cache when one is wired.
     */
    public Uni<Set<String>> resolveEffectivePermissions(String principalType, UUID principalId, String requestedPath) {
        Supplier<Uni<Set<String>>> loader = () ->
                roleAssignmentRepository.findApplicableRoleIds(principalType, principalId, requestedPath)
                        .chain(roleAssignmentRepository::findPermissionCodesForRoles)
                        .map(Set::copyOf);

        if (permissionCache == null) {
            return loader.get();
        }
        return permissionCache.getOrLoad(permissionKey(principalType, principalId, requestedPath), loader);
    }

    /**
     * Cache key for one (principal, scope path) resolution.
     *
     * <p>The principal id intentionally occupies {@link CacheKey}'s tenant slot: principals are
     * globally unique, so cross-tenant isolation still holds, and it lets us evict every scope path
     * of one principal with a single {@link Cache#invalidateTenant(UUID)} after a grant/revocation
     * (permission inheritance means a change at one path can affect many cached paths).
     */
    private static CacheKey permissionKey(String principalType, UUID principalId, String requestedPath) {
        return CacheKey.of(
                PERMISSION_CACHE_NAMESPACE,
                principalId,
                principalType.toUpperCase(java.util.Locale.ROOT) + "|" + requestedPath
        );
    }

    private Uni<Void> invalidatePrincipal(UUID principalId) {
        if (permissionCache == null) {
            return Uni.createFrom().voidItem();
        }
        return permissionCache.invalidateTenant(principalId);
    }
}
