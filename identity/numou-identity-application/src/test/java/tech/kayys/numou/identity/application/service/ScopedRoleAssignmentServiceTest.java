package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.identity.application.dto.GrantRoleAssignmentRequest;
import tech.kayys.numou.identity.application.dto.RoleAssignmentResponse;
import tech.kayys.numou.identity.application.port.IdentityOutboxPort;
import tech.kayys.numou.identity.application.port.ScopedRoleAssignmentRepository;
import tech.kayys.numou.identity.application.support.PassthroughUnitOfWork;
import tech.kayys.numou.identity.application.support.RecordingCache;
import tech.kayys.numou.identity.domain.role.RoleId;
import tech.kayys.numou.identity.domain.role.assignment.RoleAssignment;
import tech.kayys.numou.identity.domain.role.assignment.RoleAssignmentId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class ScopedRoleAssignmentServiceTest {

    private InMemoryScopedRoleAssignmentRepository repository;
    private IdentityOutboxPort outbox;
    private DomainClock clock;
    private ScopedRoleAssignmentService service;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-10-20T10:00:00Z");
        clock = () -> now;
        repository = new InMemoryScopedRoleAssignmentRepository();
        outbox = (tenantId, events) -> Uni.createFrom().voidItem();
        service = new ScopedRoleAssignmentService(repository, outbox, new PassthroughUnitOfWork(), clock);
    }

    @Test
    void shouldGrantAndResolveHierarchicalPermissions() {
        UUID userId = UUID.randomUUID();
        UUID adminRoleId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        // Assign permissions to admin role in repository mock
        repository.rolePermissions.put(new RoleId(adminRoleId), Set.of("catalog.read", "catalog.write"));

        // Grant role at scope 'org.finance'
        GrantRoleAssignmentRequest request = new GrantRoleAssignmentRequest(
                "USER",
                userId,
                adminRoleId,
                "ORGANIZATION",
                "org.finance",
                null
        );

        RoleAssignmentResponse response = service.grant(request, actorId).await().indefinitely();
        assertNotNull(response.id());
        assertEquals("org.finance", response.scopePath());
        assertEquals(actorId, response.grantedByUserId());

        // Child path 'org.finance.dataset.reports' inherits permissions from 'org.finance'
        Set<String> effectiveChild = service.resolveEffectivePermissions(
                "USER", userId, "org.finance.dataset.reports"
        ).await().indefinitely();
        assertTrue(effectiveChild.contains("catalog.read"));
        assertTrue(effectiveChild.contains("catalog.write"));
        assertEquals(2, effectiveChild.size());

        // Exact path 'org.finance' has permissions
        Set<String> effectiveExact = service.resolveEffectivePermissions(
                "USER", userId, "org.finance"
        ).await().indefinitely();
        assertTrue(effectiveExact.contains("catalog.read"));
        assertTrue(effectiveExact.contains("catalog.write"));
        assertEquals(2, effectiveExact.size());

        // Unrelated path 'org.engineering' has NO permissions
        Set<String> effectiveUnrelated = service.resolveEffectivePermissions(
                "USER", userId, "org.engineering"
        ).await().indefinitely();
        assertTrue(effectiveUnrelated.isEmpty());
    }

    @Test
    void shouldServePermissionsFromCacheAndEvictThemOnGrantAndRevoke() {
        var cache = new RecordingCache<Set<String>>(ScopedRoleAssignmentService.PERMISSION_CACHE_NAMESPACE);
        service = new ScopedRoleAssignmentService(
                repository, outbox, new PassthroughUnitOfWork(), clock, cache
        );

        UUID userId = UUID.randomUUID();
        UUID adminRoleId = UUID.randomUUID();
        UUID writerRoleId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        repository.rolePermissions.put(new RoleId(adminRoleId), Set.of("catalog.read"));
        repository.rolePermissions.put(new RoleId(writerRoleId), Set.of("catalog.write"));

        // First resolution loads from the repository, the second is served from cache.
        service.resolveEffectivePermissions("USER", userId, "org.finance").await().indefinitely();
        service.resolveEffectivePermissions("USER", userId, "org.finance").await().indefinitely();
        assertEquals(1, cache.loads(), "second resolution must hit the cache");

        // A grant must evict every cached path of that principal (inheritance!).
        service.grant(new GrantRoleAssignmentRequest(
                "USER", userId, adminRoleId, "ORGANIZATION", "org.finance", null
        ), actorId).await().indefinitely();
        assertEquals(1, cache.invalidations(), "grant must invalidate the principal's cache entries");

        Set<String> afterGrant = service.resolveEffectivePermissions(
                "USER", userId, "org.finance.dataset.reports").await().indefinitely();
        assertEquals(2, cache.loads(), "resolution after the grant must reload");
        assertTrue(afterGrant.contains("catalog.read"));

        // Cached again...
        service.resolveEffectivePermissions("USER", userId, "org.finance.dataset.reports").await().indefinitely();
        assertEquals(2, cache.loads());

        // ...and evicted again on revoke, so a revoked permission never lingers in cache.
        service.revoke("USER", userId, adminRoleId, actorId, "org.finance").await().indefinitely();
        assertEquals(2, cache.invalidations(), "revoke must invalidate the principal's cache entries");

        Set<String> afterRevoke = service.resolveEffectivePermissions(
                "USER", userId, "org.finance.dataset.reports").await().indefinitely();
        assertEquals(3, cache.loads(), "resolution after the revoke must reload");
        assertTrue(afterRevoke.isEmpty(), "revoked permissions must not be served from cache");
    }

    @Test
    void shouldRevokeAssignment() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        service.grant(new GrantRoleAssignmentRequest(
                "USER", userId, roleId, "WORKSPACE", "org.sales.ws1", null
        ), actorId).await().indefinitely();

        assertEquals(1, service.listForPrincipal("USER", userId).await().indefinitely().size());

        service.revoke("USER", userId, roleId, actorId, "org.sales.ws1").await().indefinitely();
        assertTrue(service.listForPrincipal("USER", userId).await().indefinitely().isEmpty());
    }

    static class InMemoryScopedRoleAssignmentRepository implements ScopedRoleAssignmentRepository {
        private final Map<RoleAssignmentId, RoleAssignment> store = new ConcurrentHashMap<>();
        final Map<RoleId, Set<String>> rolePermissions = new HashMap<>();

        @Override
        public Uni<Void> save(RoleAssignment assignment) {
            store.put(assignment.id(), assignment);
            return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<Long> deleteByPrincipalAndRole(String principalType, UUID principalId, RoleId roleId, String scopePath) {
            List<RoleAssignmentId> toRemove = store.values().stream()
                    .filter(a -> a.principalType().equalsIgnoreCase(principalType)
                            && a.principalId().equals(principalId)
                            && a.roleId().equals(roleId)
                            && a.scopePath().equals(scopePath))
                    .map(RoleAssignment::id)
                    .toList();
            toRemove.forEach(store::remove);
            return Uni.createFrom().item((long) toRemove.size());
        }

        @Override
        public Uni<List<RoleAssignment>> findByPrincipal(String principalType, UUID principalId) {
            List<RoleAssignment> list = store.values().stream()
                    .filter(a -> a.principalType().equalsIgnoreCase(principalType) && a.principalId().equals(principalId))
                    .toList();
            return Uni.createFrom().item(new ArrayList<>(list));
        }

        @Override
        public Uni<List<RoleId>> findApplicableRoleIds(String principalType, UUID principalId, String requestedPath) {
            // Find all assignments where requestedPath == scopePath or requestedPath starts with scopePath + "."
            List<RoleId> roles = new ArrayList<>();
            for (RoleAssignment a : store.values()) {
                if (a.principalType().equalsIgnoreCase(principalType) && a.principalId().equals(principalId)) {
                    if (requestedPath.equals(a.scopePath()) || requestedPath.startsWith(a.scopePath() + ".")) {
                        roles.add(a.roleId());
                    }
                }
            }
            return Uni.createFrom().item(roles);
        }

        @Override
        public Uni<List<RoleAssignment>> findHolders(String principalType, RoleId roleId, String scopePath) {
            List<RoleAssignment> list = store.values().stream()
                    .filter(a -> a.roleId().equals(roleId) && a.scopePath().equals(scopePath))
                    .toList();
            return Uni.createFrom().item(new ArrayList<>(list));
        }

        @Override
        public Uni<Set<String>> findPermissionCodesForRoles(List<RoleId> roleIds) {
            Set<String> perms = new java.util.HashSet<>();
            for (RoleId r : roleIds) {
                Set<String> p = rolePermissions.get(r);
                if (p != null) perms.addAll(p);
            }
            return Uni.createFrom().item(perms);
        }
    }
}
