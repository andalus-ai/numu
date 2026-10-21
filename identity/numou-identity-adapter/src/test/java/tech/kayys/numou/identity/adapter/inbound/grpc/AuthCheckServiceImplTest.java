package tech.kayys.numou.identity.adapter.inbound.grpc;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.application.page.Page;
import tech.kayys.numou.foundation.application.page.PageRequest;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.identity.application.dto.CreatePatRequest;
import tech.kayys.numou.identity.application.dto.CreatePatResponse;
import tech.kayys.numou.identity.application.dto.GrantRoleAssignmentRequest;
import tech.kayys.numou.identity.application.port.IdentityOutboxPort;
import tech.kayys.numou.identity.application.port.PasswordHasher;
import tech.kayys.numou.identity.application.port.PersonalAccessTokenRepository;
import tech.kayys.numou.identity.application.port.ScopedRoleAssignmentRepository;
import tech.kayys.numou.identity.application.port.TokenVerifierPort;
import tech.kayys.numou.identity.application.port.UserRepository;
import tech.kayys.numou.identity.application.service.PersonalAccessTokenService;
import tech.kayys.numou.identity.application.service.ScopedRoleAssignmentService;
import tech.kayys.numou.identity.application.service.TokenService;
import tech.kayys.numou.identity.domain.role.RoleId;
import tech.kayys.numou.identity.domain.role.assignment.RoleAssignment;
import tech.kayys.numou.identity.domain.role.assignment.RoleAssignmentId;
import tech.kayys.numou.identity.domain.token.PersonalAccessToken;
import tech.kayys.numou.identity.domain.token.PersonalAccessTokenId;
import tech.kayys.numou.identity.domain.user.User;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.identity.domain.valueobject.EmailAddress;
import tech.kayys.numou.identity.domain.valueobject.PasswordHash;
import tech.kayys.numou.identity.grpc.CheckPermissionRequest;
import tech.kayys.numou.identity.grpc.CheckPermissionResponse;
import tech.kayys.numou.identity.grpc.CheckPermissionsRequest;
import tech.kayys.numou.identity.grpc.CheckPermissionsResponse;
import tech.kayys.numou.identity.grpc.PathPermissionPair;
import tech.kayys.numou.identity.grpc.PrincipalType;
import tech.kayys.numou.identity.grpc.ResolvePrincipalRequest;
import tech.kayys.numou.identity.grpc.ResolvePrincipalResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class AuthCheckServiceImplTest {

    private AuthCheckServiceImpl grpcService;
    private ScopedRoleAssignmentService roleAssignmentService;
    private PersonalAccessTokenService patService;
    private TokenService tokenService;
    private StubScopedRoleAssignmentRepository roleRepo;
    private StubPatRepository patRepo;

    @BeforeEach
    void setUp() {
        Instant now = Instant.parse("2026-10-20T10:00:00Z");
        DomainClock clock = () -> now;
        IdentityOutboxPort outbox = (tenantId, events) -> Uni.createFrom().voidItem();
        UnitOfWork unitOfWork = new UnitOfWork() {
            @Override
            public <R> Uni<R> execute(Supplier<Uni<R>> work) {
                return work.get();
            }
        };

        roleRepo = new StubScopedRoleAssignmentRepository();
        roleAssignmentService = new ScopedRoleAssignmentService(
                roleRepo, outbox, unitOfWork, clock
        );

        patRepo = new StubPatRepository();
        patService = new PersonalAccessTokenService(
                patRepo, outbox, unitOfWork, clock
        );

        PasswordHasher stubHasher = new PasswordHasher() {
            @Override
            public PasswordHash hash(String rawPassword) {
                return PasswordHash.of("hashed_" + rawPassword);
            }

            @Override
            public boolean matches(String rawPassword, PasswordHash hash) {
                return true;
            }
        };

        tokenService = new TokenService(
                new StubUserRepository(),
                stubHasher,
                (id, type, name) -> "valid_token_for_" + id,
                raw -> {
                    if ("valid_jwt".equals(raw)) {
                        return Uni.createFrom().item(new TokenVerifierPort.VerifiedToken(
                                "USER", UUID.fromString("11111111-1111-1111-1111-111111111111"), "Alice Developer"
                        ));
                    }
                    return Uni.createFrom().failure(new IllegalArgumentException("invalid token"));
                },
                900,
                patService
        );

        grpcService = new AuthCheckServiceImpl(roleAssignmentService, patService, tokenService);
    }

    @Test
    void shouldAllowWhenPermissionPresent() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        roleRepo.rolePermissions.put(new RoleId(roleId), Set.of("dataset.read", "dataset.write"));

        roleAssignmentService.grant(new GrantRoleAssignmentRequest(
                "USER", userId, roleId, "WORKSPACE", "org.dataset.sales", null
        ), userId).await().indefinitely();

        CheckPermissionRequest request = CheckPermissionRequest.newBuilder()
                .setPrincipalType(PrincipalType.USER)
                .setPrincipalId(userId.toString())
                .setRequestedPath("org.dataset.sales")
                .setRequiredPermission("dataset.read")
                .build();

        CheckPermissionResponse response = grpcService.checkPermission(request).await().indefinitely();
        assertTrue(response.getAllowed());
    }

    @Test
    void shouldDenyWhenPermissionMissing() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        roleRepo.rolePermissions.put(new RoleId(roleId), Set.of("dataset.read"));

        roleAssignmentService.grant(new GrantRoleAssignmentRequest(
                "USER", userId, roleId, "WORKSPACE", "org.dataset.sales", null
        ), userId).await().indefinitely();

        CheckPermissionRequest request = CheckPermissionRequest.newBuilder()
                .setPrincipalType(PrincipalType.USER)
                .setPrincipalId(userId.toString())
                .setRequestedPath("org.dataset.sales")
                .setRequiredPermission("dataset.delete")
                .build();

        CheckPermissionResponse response = grpcService.checkPermission(request).await().indefinitely();
        assertFalse(response.getAllowed());
    }

    @Test
    void shouldFailClosedOnInvalidPrincipalId() {
        CheckPermissionRequest request = CheckPermissionRequest.newBuilder()
                .setPrincipalType(PrincipalType.USER)
                .setPrincipalId("not-a-valid-uuid")
                .setRequestedPath("org.dataset.sales")
                .setRequiredPermission("dataset.read")
                .build();

        CheckPermissionResponse response = grpcService.checkPermission(request).await().indefinitely();
        assertFalse(response.getAllowed());
    }

    @Test
    void shouldCheckPermissionsInBatch() {
        UUID userId = UUID.randomUUID();
        UUID roleIdA = UUID.randomUUID();
        UUID roleIdB = UUID.randomUUID();
        roleRepo.rolePermissions.put(new RoleId(roleIdA), Set.of("perm.1"));
        roleRepo.rolePermissions.put(new RoleId(roleIdB), Set.of("perm.2"));

        roleAssignmentService.grant(new GrantRoleAssignmentRequest(
                "USER", userId, roleIdA, "WORKSPACE", "path.a", null
        ), userId).await().indefinitely();
        roleAssignmentService.grant(new GrantRoleAssignmentRequest(
                "USER", userId, roleIdB, "WORKSPACE", "path.b", null
        ), userId).await().indefinitely();

        CheckPermissionsRequest request = CheckPermissionsRequest.newBuilder()
                .setPrincipalType(PrincipalType.USER)
                .setPrincipalId(userId.toString())
                .addChecks(PathPermissionPair.newBuilder().setRequestedPath("path.a").setRequiredPermission("perm.1").build())
                .addChecks(PathPermissionPair.newBuilder().setRequestedPath("path.a").setRequiredPermission("perm.missing").build())
                .addChecks(PathPermissionPair.newBuilder().setRequestedPath("path.b").setRequiredPermission("perm.2").build())
                .build();

        CheckPermissionsResponse response = grpcService.checkPermissions(request).await().indefinitely();
        assertEquals(List.of(true, false, true), response.getResultsList());
    }

    @Test
    void shouldResolvePatPrincipal() {
        UUID userId = UUID.randomUUID();
        CreatePatResponse issued = patService.issue(new CreatePatRequest(
                "USER", userId, "CLI Token", null
        )).await().indefinitely();

        ResolvePrincipalResponse response = grpcService.resolvePrincipal(
                ResolvePrincipalRequest.newBuilder().setCredential(issued.token()).build()
        ).await().indefinitely();

        assertTrue(response.getValid());
        assertEquals(PrincipalType.USER, response.getPrincipalType());
        assertEquals(userId.toString(), response.getPrincipalId());
        assertEquals("CLI Token", response.getDisplayName());
    }

    @Test
    void shouldResolveJwtPrincipal() {
        ResolvePrincipalResponse response = grpcService.resolvePrincipal(
                ResolvePrincipalRequest.newBuilder().setCredential("valid_jwt").build()
        ).await().indefinitely();

        assertTrue(response.getValid());
        assertEquals(PrincipalType.USER, response.getPrincipalType());
        assertEquals("11111111-1111-1111-1111-111111111111", response.getPrincipalId());
        assertEquals("Alice Developer", response.getDisplayName());
    }

    static class StubScopedRoleAssignmentRepository implements ScopedRoleAssignmentRepository {
        private final Map<RoleAssignmentId, RoleAssignment> store = new ConcurrentHashMap<>();
        final Map<RoleId, Set<String>> rolePermissions = new HashMap<>();

        @Override
        public Uni<Void> save(RoleAssignment assignment) {
            store.put(assignment.id(), assignment);
            return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<Long> deleteByPrincipalAndRole(String principalType, UUID principalId, RoleId roleId, String scopePath) {
            return Uni.createFrom().item(0L);
        }

        @Override
        public Uni<List<RoleAssignment>> findByPrincipal(String principalType, UUID principalId) {
            return Uni.createFrom().item(new ArrayList<>(store.values()));
        }

        @Override
        public Uni<List<RoleId>> findApplicableRoleIds(String principalType, UUID principalId, String requestedPath) {
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

    static class StubPatRepository implements PersonalAccessTokenRepository {
        private final Map<PersonalAccessTokenId, PersonalAccessToken> store = new ConcurrentHashMap<>();

        @Override
        public Uni<Void> save(PersonalAccessToken pat) {
            store.put(pat.id(), pat);
            return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<Optional<PersonalAccessToken>> findById(PersonalAccessTokenId id) {
            return Uni.createFrom().item(Optional.ofNullable(store.get(id)));
        }

        @Override
        public Uni<Optional<PersonalAccessToken>> findByTokenHash(String tokenHash) {
            return Uni.createFrom().item(store.values().stream()
                    .filter(p -> p.tokenHash().equals(tokenHash))
                    .findFirst());
        }

        @Override
        public Uni<List<PersonalAccessToken>> findByPrincipal(String principalType, UUID principalId) {
            return Uni.createFrom().item(new ArrayList<>(store.values()));
        }
    }

    static class StubUserRepository implements UserRepository {
        @Override
        public Uni<Void> save(User user) {
            return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<Optional<User>> findById(UserId id) {
            return Uni.createFrom().item(Optional.empty());
        }

        @Override
        public Uni<Optional<User>> findByEmail(EmailAddress email) {
            return Uni.createFrom().item(Optional.empty());
        }

        @Override
        public Uni<Page<User>> findAll(PageRequest pageRequest) {
            return Uni.createFrom().item(Page.empty(pageRequest));
        }
    }
}
