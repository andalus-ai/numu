package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.identity.application.dto.FederationSettings;
import tech.kayys.numou.identity.application.dto.ScimUser;
import tech.kayys.numou.identity.application.dto.ScimUserWrite;
import tech.kayys.numou.identity.application.port.FederatedIdentityRepository;
import tech.kayys.numou.identity.application.port.FederationSettingsPort;
import tech.kayys.numou.identity.application.port.IdentityProviderConfigRepository;
import tech.kayys.numou.identity.application.port.PasswordHasher;
import tech.kayys.numou.identity.application.port.ScopedRoleAssignmentRepository;
import tech.kayys.numou.identity.application.port.UserRepository;
import tech.kayys.numou.identity.application.support.InMemoryPasswordHasher;
import tech.kayys.numou.identity.application.support.InMemoryUserRepository;
import tech.kayys.numou.identity.application.support.PassthroughUnitOfWork;
import tech.kayys.numou.identity.application.support.RecordingEventPublisher;
import tech.kayys.numou.identity.domain.idp.FederatedIdentityLink;
import tech.kayys.numou.identity.domain.idp.IdentityProviderConfig;
import tech.kayys.numou.identity.domain.idp.IdentityProviderConfigId;
import tech.kayys.numou.identity.domain.idp.IdentityProviderType;
import tech.kayys.numou.identity.domain.role.RoleId;
import tech.kayys.numou.identity.domain.role.assignment.RoleAssignment;
import tech.kayys.numou.identity.domain.role.assignment.RoleAssignmentId;
import tech.kayys.numou.identity.domain.role.assignment.RoleScopeType;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.identity.domain.valueobject.EmailAddress;
import tech.kayys.numou.identity.domain.valueobject.PasswordHash;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class ScimProvisioningServiceContractTest {

    private UUID providerId;
    private UUID userId;
    private UUID adminRoleId;
    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    private InMemoryUserRepository userRepository;
    private RecordingEventPublisher eventPublisher;
    private InMemoryScopedRoleAssignmentRepository roleAssignmentRepository;
    private InMemoryFederatedIdentityRepository federatedIdentities;
    private InMemoryIdentityProviderConfigRepository providerConfigs;
    private InMemoryFederationSettingsPort settingsPort;
    private ScimProvisioningService service;

    @BeforeEach
    void setUp() {
        DomainClock clock = () -> NOW;

        providerId = UUID.randomUUID();
        userId = UUID.randomUUID();
        adminRoleId = UUID.randomUUID();

        userRepository = new InMemoryUserRepository();
        eventPublisher = new RecordingEventPublisher();
        roleAssignmentRepository = new InMemoryScopedRoleAssignmentRepository();
        federatedIdentities = new InMemoryFederatedIdentityRepository();
        providerConfigs = new InMemoryIdentityProviderConfigRepository(providerId);
        settingsPort = new InMemoryFederationSettingsPort(adminRoleId);

        var roleAssignmentService = new ScopedRoleAssignmentService(
                roleAssignmentRepository,
                (tenantId, events) -> Uni.createFrom().voidItem(),
                new PassthroughUnitOfWork(),
                clock,
                null
        );

        service = new ScimProvisioningService(
                providerConfigs,
                settingsPort,
                federatedIdentities,
                userRepository,
                roleAssignmentService,
                roleAssignmentRepository,
                new InMemoryPasswordHasher(),
                eventPublisher,
                new PassthroughUnitOfWork(),
                clock
        );
    }
    @Test
    void shouldCreateNewUserAndLinkProviderSubject() {
        ScimUserWrite write = new ScimUserWrite(
                "alice@example.com", "ext-subject-1", "Alice Smith",
                "alice@example.com", true
        );
        ScimUser user = service.createOrAdoptUser(providerId, write).await().indefinitely();
        assertNotNull(user.id());
        assertEquals("alice@example.com", user.userName());
        assertEquals("ext-subject-1", user.externalId());
        assertEquals("Alice Smith", user.displayName());
        assertTrue(user.active());
        var link = federatedIdentities.findByProviderAndSubject(providerId, "ext-subject-1")
                .await().indefinitely();
        assertTrue(link.isPresent());
        assertEquals(user.id(), link.get().userId());
    }
    @Test
    void shouldAdoptExistingUserByEmailWhenExternalIdIsNew() {
        var existing = tech.kayys.numou.identity.domain.user.User.register(
                UserId.of(userId), EmailAddress.of("bob@example.com"),
                new PasswordHash("hashed:pw"),
                new tech.kayys.numou.identity.domain.valueobject.DisplayName("Bob"), NOW
        );
        existing.activate(NOW);
        userRepository.save(existing).await().indefinitely();
        ScimUserWrite write = new ScimUserWrite(
                "bob@example.com", "ext-subject-2", null, "bob@example.com", true
        );
        ScimUser user = service.createOrAdoptUser(providerId, write).await().indefinitely();
        assertEquals(userId, user.id());
        assertEquals("ext-subject-2", user.externalId());
        var link = federatedIdentities.findByProviderAndSubject(providerId, "ext-subject-2")
                .await().indefinitely();
        assertTrue(link.isPresent());
        assertEquals(userId, link.get().userId());
    }

    @Test
    void shouldBeIdempotentOnExternalId() {
        ScimUserWrite write = new ScimUserWrite(
                "carol@example.com", "ext-subject-3", "Carol", "carol@example.com", true
        );
        ScimUser first = service.createOrAdoptUser(providerId, write).await().indefinitely();
        ScimUser second = service.createOrAdoptUser(providerId, write).await().indefinitely();
        assertEquals(first.id(), second.id());
    }
    @Test
    void shouldGetUserAsScimResource() {
        ScimUserWrite write = new ScimUserWrite(
                "dave@example.com", "ext-subject-4", "Dave", "dave@example.com", true
        );
        ScimUser created = service.createOrAdoptUser(providerId, write).await().indefinitely();
        ScimUser fetched = service.getUser(providerId, created.id()).await().indefinitely();
        assertEquals(created.id(), fetched.id());
        assertEquals("dave@example.com", fetched.userName());
        assertEquals("ext-subject-4", fetched.externalId());
        assertEquals("Dave", fetched.displayName());
        assertTrue(fetched.active());
    }

    @Test
    void shouldReplaceUserDetails() {
        ScimUserWrite write = new ScimUserWrite(
                "erin@example.com", "ext-subject-5", "Erin", "erin@example.com", true
        );
        ScimUser created = service.createOrAdoptUser(providerId, write).await().indefinitely();
        ScimUserWrite update = new ScimUserWrite(
                "erin.new@example.com", null, "Erin Updated", "erin.new@example.com", true
        );
        ScimUser replaced = service.replaceUser(providerId, created.id(), update).await().indefinitely();
        assertEquals(created.id(), replaced.id());
        assertEquals("erin.new@example.com", replaced.userName());
        assertEquals("Erin Updated", replaced.displayName());
        assertEquals("erin.new@example.com", replaced.email());
    }

    @Test
    void shouldThrowWhenGettingUnknownUser() {
        assertThrows(RuntimeException.class, () ->
                service.getUser(providerId, UUID.randomUUID()).await().indefinitely()
        );
    }
    @Test
    void shouldPatchActiveFlagToFalseAndReactivate() {
        ScimUserWrite write = new ScimUserWrite(
                "frank@example.com", "ext-subject-6", "Frank", "frank@example.com", true
        );
        ScimUser created = service.createOrAdoptUser(providerId, write).await().indefinitely();
        var patchOps = List.of(
                new tech.kayys.numou.identity.application.dto.ScimPatchOperation("replace", "active", false)
        );
        ScimUser deactivated = service.patchUser(
                providerId, created.id(), patchOps
        ).await().indefinitely();
        assertFalse(deactivated.active());
        var reactivateOps = List.of(
                new tech.kayys.numou.identity.application.dto.ScimPatchOperation("replace", "active", true)
        );
        ScimUser reactivated = service.patchUser(
                providerId, created.id(), reactivateOps
        ).await().indefinitely();
        assertTrue(reactivated.active());
    }

    @Test
    void shouldDeactivateUserAndRevokeAllScopedRoleAssignments() {
        ScimUserWrite write = new ScimUserWrite(
                "grace@example.com", "ext-subject-7", "Grace", "grace@example.com", true
        );
        ScimUser created = service.createOrAdoptUser(providerId, write).await().indefinitely();
        RoleAssignment grant1 = RoleAssignment.grant(
                RoleAssignmentId.newId(), "USER", created.id(),
                new RoleId(adminRoleId), RoleScopeType.ORGANIZATION, "org.finance",
                null, null, NOW
        );
        RoleAssignment grant2 = RoleAssignment.grant(
                RoleAssignmentId.newId(), "USER", created.id(),
                new RoleId(adminRoleId), RoleScopeType.ORGANIZATION, "org.finance.dataset.reports",
                null, null, NOW
        );
        roleAssignmentRepository.save(grant1).await().indefinitely();
        roleAssignmentRepository.save(grant2).await().indefinitely();
        var before = roleAssignmentRepository.findByPrincipal("USER", created.id())
                .await().indefinitely();
        assertEquals(2, before.size());
        service.deprovisionUser(providerId, created.id()).await().indefinitely();
        var user = userRepository.findById(UserId.of(created.id())).await().indefinitely();
        assertTrue(user.isPresent());
        assertFalse(user.get().isActive());
        var after = roleAssignmentRepository.findByPrincipal("USER", created.id())
                .await().indefinitely();
        assertTrue(after.isEmpty(), "all scoped role assignments must be revoked on deactivation");
    }

    @Test
    void shouldDeprovisionIdempotentlyWhenAlreadyInactive() {
        ScimUserWrite write = new ScimUserWrite(
                "heidi@example.com", "ext-subject-8", "Heidi", "heidi@example.com", true
        );
        ScimUser created = service.createOrAdoptUser(providerId, write).await().indefinitely();
        RoleAssignment grant = RoleAssignment.grant(
                RoleAssignmentId.newId(), "USER", created.id(),
                new RoleId(adminRoleId), RoleScopeType.ORGANIZATION, "org.finance",
                null, null, NOW
        );
        roleAssignmentRepository.save(grant).await().indefinitely();
        var patchOps = List.of(
                new tech.kayys.numou.identity.application.dto.ScimPatchOperation("replace", "active", false)
        );
        service.patchUser(providerId, created.id(), patchOps)
                .await().indefinitely();
        service.deprovisionUser(providerId, created.id()).await().indefinitely();
        var after = roleAssignmentRepository.findByPrincipal("USER", created.id())
                .await().indefinitely();
        assertTrue(after.isEmpty(), "role assignments must be revoked even when already inactive");
    }

    @Test
    void shouldNotFailDeprovisionWhenUserHasNoAssignments() {
        ScimUserWrite write = new ScimUserWrite(
                "ivan@example.com", "ext-subject-9", "Ivan", "ivan@example.com", true
        );
        ScimUser created = service.createOrAdoptUser(providerId, write).await().indefinitely();
        service.deprovisionUser(providerId, created.id()).await().indefinitely();
        var user = userRepository.findById(UserId.of(created.id())).await().indefinitely();
        assertTrue(user.isPresent());
        assertFalse(user.get().isActive());
    }

    @Test
    void shouldThrowWhenDeprovisioningUnknownUser() {
        assertThrows(RuntimeException.class, () ->
                service.deprovisionUser(providerId, UUID.randomUUID()).await().indefinitely()
        );
    }
    static class InMemoryScopedRoleAssignmentRepository implements ScopedRoleAssignmentRepository {

        private final java.util.Map<RoleAssignmentId, RoleAssignment> store = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public Uni<List<tech.kayys.numou.identity.domain.role.RoleId>> findApplicableRoleIds(
                String principalType, UUID principalId, String requestedPath
        ) {
            return Uni.createFrom().item(List.of());
        }

        @Override
        public Uni<List<RoleAssignment>> findByPrincipal(String principalType, UUID principalId) {
            List<RoleAssignment> list = store.values().stream()
                    .filter(a -> a.principalType().equalsIgnoreCase(principalType)
                            && a.principalId().equals(principalId))
                    .toList();
            return Uni.createFrom().item(java.util.Collections.unmodifiableList(list));
        }

        @Override
        public Uni<List<RoleAssignment>> findHolders(
                String principalType, RoleId roleId, String scopePath
        ) {
            return Uni.createFrom().item(List.of());
        }

        @Override
        public Uni<Void> save(RoleAssignment assignment) {
            store.put(assignment.id(), assignment);
            return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<Long> deleteByPrincipalAndRole(
                String principalType, UUID principalId, RoleId roleId, String scopePath
        ) {
            boolean removed = store.values().removeIf(a ->
                    a.principalType().equalsIgnoreCase(principalType)
                            && a.principalId().equals(principalId)
                            && a.roleId().equals(roleId)
                            && a.scopePath().equals(scopePath)
            );
            return Uni.createFrom().item(removed ? 1L : 0L);
        }

        @Override
        public Uni<Long> deleteByPrincipal(String principalType, UUID principalId) {
            boolean removed = store.values().removeIf(a ->
                    a.principalType().equalsIgnoreCase(principalType)
                            && a.principalId().equals(principalId)
            );
            return Uni.createFrom().item(removed ? 1L : 0L);
        }

        @Override
        public Uni<java.util.Set<String>> findPermissionCodesForRoles(
                java.util.List<RoleId> roleIds
        ) {
            return Uni.createFrom().item(java.util.Set.of());
        }
    }
    static class InMemoryFederatedIdentityRepository implements FederatedIdentityRepository {

        private final java.util.Map<String, FederatedIdentityLink> byProviderSubject = new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.Map<UUID, FederatedIdentityLink> byUserId = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public Uni<Optional<FederatedIdentityLink>> findByProviderAndSubject(
                UUID providerId, String subject
        ) {
            return Uni.createFrom().item(Optional.ofNullable(
                    byProviderSubject.get(providerId + "|" + subject)
            ));
        }

        @Override
        public Uni<Optional<FederatedIdentityLink>> findByUserId(UUID userId) {
            return Uni.createFrom().item(Optional.ofNullable(byUserId.get(userId)));
        }

        @Override
        public Uni<Void> save(FederatedIdentityLink link) {
            byProviderSubject.put(link.providerId() + "|" + link.subject(), link);
            byUserId.put(link.userId(), link);
            return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<Void> save(FederatedIdentityLink link, java.util.Map<String, String> claims) {
            return save(link);
        }
    }
    static class InMemoryIdentityProviderConfigRepository implements IdentityProviderConfigRepository {

        private final UUID providerId;

        InMemoryIdentityProviderConfigRepository(UUID providerId) {
            this.providerId = providerId;
        }

        @Override
        public Uni<Optional<IdentityProviderConfig>> findById(IdentityProviderConfigId id) {
            if (!id.value().equals(providerId)) {
                return Uni.createFrom().item(Optional.empty());
            }
            IdentityProviderConfig config = IdentityProviderConfig.create(
                    IdentityProviderConfigId.of(providerId),
                    "Test SCIM Provider",
                    IdentityProviderType.LOCAL,
                    "{}",
                    "https://scim.example.com/v2",
                    NOW
            );
            return Uni.createFrom().item(Optional.of(config));
        }

        @Override
        public Uni<Optional<IdentityProviderConfig>> findByName(String name) {
            return Uni.createFrom().item(Optional.empty());
        }

        @Override
        public Uni<List<IdentityProviderConfig>> findEnabled() {
            return Uni.createFrom().item(List.of());
        }

        @Override
        public Uni<Void> save(IdentityProviderConfig config) {
            return Uni.createFrom().voidItem();
        }
    }

    static class InMemoryFederationSettingsPort implements FederationSettingsPort {

        private final UUID adminRoleId;

        InMemoryFederationSettingsPort(UUID adminRoleId) {
            this.adminRoleId = adminRoleId;
        }

        @Override
        public FederationSettings parse(tech.kayys.numou.identity.domain.idp.IdentityProviderConfig config) {
            return new FederationSettings(
                    "https://idp.example", "", "", "",
                    List.of("openid", "email", "profile"), "", "", "",
                    Map.of("admins", adminRoleId.toString()), "org.finance", "ORGANIZATION",
                    "", "", "", "", "", "", "",
                    "", "", "", "",
                    List.<String>of(), List.<String>of()
            );
        }
    }
}
