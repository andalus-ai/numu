package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.identity.application.dto.FederationAuthorizationRequest;
import tech.kayys.numou.identity.application.dto.FederationCallback;
import tech.kayys.numou.identity.application.dto.FederationSettings;
import tech.kayys.numou.identity.application.dto.FederatedAuthorizationStart;
import tech.kayys.numou.identity.application.dto.FederatedLoginResponse;
import tech.kayys.numou.identity.application.port.FederatedIdentityProviderPort;
import tech.kayys.numou.identity.application.port.FederatedIdentityRepository;
import tech.kayys.numou.identity.application.port.FederationSettingsPort;
import tech.kayys.numou.identity.application.port.IdentityProviderConfigRepository;
import tech.kayys.numou.identity.application.support.InMemoryPasswordHasher;
import tech.kayys.numou.identity.application.support.InMemoryUserRepository;
import tech.kayys.numou.identity.application.support.PassthroughUnitOfWork;
import tech.kayys.numou.identity.application.support.RecordingEventPublisher;
import tech.kayys.numou.identity.domain.event.UserActivated;
import tech.kayys.numou.identity.domain.event.UserRegistered;
import tech.kayys.numou.identity.domain.idp.FederatedIdentityLink;
import tech.kayys.numou.identity.domain.idp.IdentityProviderConfig;
import tech.kayys.numou.identity.domain.idp.IdentityProviderConfigId;
import tech.kayys.numou.identity.domain.idp.IdentityProviderType;
import tech.kayys.numou.identity.domain.role.RoleId;
import tech.kayys.numou.identity.domain.user.FederatedIdentity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Just-In-Time provisioning + group-to-role synchronization for the OIDC federation flow,
 * exercised against a fake {@link FederatedIdentityProviderPort} (protocol details belong to
 * the adapter's own tests; these are the identity decisions).
 */
class FederatedAuthenticationServiceTest {

    private static final UUID PROVIDER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000ff");
    private static final UUID ROLE_ID = UUID.fromString("00000000-0000-4000-8000-000000000010");

    private final Instant now = Instant.parse("2026-10-20T10:00:00Z");
    private final DomainClock clock = () -> now;

    private InMemoryUserRepository users;
    private InMemoryFederatedIdentities links;
    private ScopedRoleAssignmentService roleAssignments;
    private RecordingEventPublisher events;
    private FakeProviderPort provider;
    private FederatedAuthenticationService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        links = new InMemoryFederatedIdentities();
        events = new RecordingEventPublisher();

        var roleRepository = new ScopedRoleAssignmentServiceTest.InMemoryScopedRoleAssignmentRepository();
        roleRepository.rolePermissions.put(new RoleId(ROLE_ID), Set.of("identity.user.manage"));
        roleAssignments = new ScopedRoleAssignmentService(
                roleRepository, (tenantId, published) -> Uni.createFrom().voidItem(),
                new PassthroughUnitOfWork(), clock
        );

        provider = new FakeProviderPort();
        IdentityProviderConfig config = IdentityProviderConfig.create(
                IdentityProviderConfigId.of(PROVIDER_ID),
                "Corp Okta",
                IdentityProviderType.OIDC,
                "{}",
                null,
                now
        );
        IdentityProviderConfigRepository configs = new SingleProviderConfigs(config);
        FederationSettingsPort settingsPort = ignored -> new FederationSettings(
                "https://idp.example", "numou", "secret",
                "https://app.example/api/v1/auth/federated/" + PROVIDER_ID + "/callback",
                List.of("openid", "email"),
                "", "", "",
                Map.of("admins", ROLE_ID.toString()),
                "system",
                "SYSTEM"
        );

        var tokenService = new TokenService(
                users,
                new InMemoryPasswordHasher(),
                (principalId, principalType, displayName) -> "internal-token-for-" + principalId,
                raw -> Uni.createFrom().failure(new IllegalArgumentException("not used in this test")),
                900
        );

        service = new FederatedAuthenticationService(
                configs, provider, settingsPort, links, users, roleAssignments,
                new InMemoryPasswordHasher(), events, tokenService,
                new PassthroughUnitOfWork(), clock
        );
    }

    @Test
    void shouldStartAuthorizationFlowWithFreshStateNonceAndPkceVerifier() throws Exception {
        FederatedAuthorizationStart start = service.begin(PROVIDER_ID).await().indefinitely();

        assertFalse(start.state().isBlank());
        assertFalse(start.nonce().isBlank());
        assertFalse(start.codeVerifier().isBlank());
        assertNotEquals(start.state(), start.nonce());
        assertTrue(start.authorizationUrl().contains("state=" + start.state()));

        // RFC 7636 S256 - recomputed here so the test does not trust the implementation under test.
        var expectedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256")
                        .digest(start.codeVerifier().getBytes(StandardCharsets.US_ASCII)));
        assertEquals(expectedChallenge, FederatedAuthenticationService.pkceChallenge(start.codeVerifier()));
    }

    @Test
    void shouldProvisionUserGrantMappedRoleAndIssueTokenOnFirstLogin() {
        provider.identity = new FederatedIdentity(
                PROVIDER_ID.toString(), "sub-alice", "alice@corp.example",
                "Alice Corp", Set.of("admins"), Map.of()
        );

        FederatedLoginResponse response = login();

        assertTrue(response.provisioned(), "first login must JIT-provision the internal user");
        assertEquals("internal-token-for-" + response.userId(), response.token().accessToken());
        assertEquals(Set.of("admins"), response.groups());

        var assignments = roleAssignments.listForPrincipal("USER", response.userId())
                .await().indefinitely();
        assertEquals(1, assignments.size(), "the IdP group must map to exactly one role grant");
        assertEquals(ROLE_ID, assignments.get(0).roleId());
        assertEquals("system", assignments.get(0).scopePath());

        assertTrue(events.published().stream().anyMatch(UserRegistered.class::isInstance));
        assertTrue(events.published().stream().anyMatch(UserActivated.class::isInstance));
        assertTrue(links.findByProviderAndSubject(PROVIDER_ID, "sub-alice").await().indefinitely().isPresent());
    }

    @Test
    void shouldReuseExistingLinkAndNotDuplicateGrantsOnSecondLogin() {
        provider.identity = new FederatedIdentity(
                PROVIDER_ID.toString(), "sub-alice", "alice@corp.example",
                "Alice Corp", Set.of("admins"), Map.of()
        );

        FederatedLoginResponse first = login();
        FederatedLoginResponse second = login();

        assertFalse(second.provisioned(), "the second login must reuse the federation link");
        assertEquals(first.userId(), second.userId());
        assertEquals(1, roleAssignments.listForPrincipal("USER", first.userId())
                .await().indefinitely().size(), "repeated logins must not pile up duplicate grants");
    }

    @Test
    void shouldRejectJitProvisioningWhenProviderAssertsNoEmail() {
        provider.identity = new FederatedIdentity(
                PROVIDER_ID.toString(), "sub-noemail", "",
                null, Set.of(), Map.of()
        );

        var failure = assertThrows(ApplicationErrorException.class, this::login);
        assertEquals("FEDERATED_EMAIL_REQUIRED", failure.error().code());
    }

    private FederatedLoginResponse login() {
        return service.complete(PROVIDER_ID, new FederationCallback(
                "auth-code", "state", "verifier", "nonce"
        )).await().indefinitely();
    }

    // ------------------------------------------------------------------ test doubles

    /** Returns exactly one provider config; everything else 404s/empties. */
    static class SingleProviderConfigs implements IdentityProviderConfigRepository {
        private final IdentityProviderConfig config;

        SingleProviderConfigs(IdentityProviderConfig config) {
            this.config = config;
        }

        @Override
        public Uni<Optional<IdentityProviderConfig>> findById(IdentityProviderConfigId id) {
            return Uni.createFrom().item(
                    Optional.of(config).filter(c -> c.id().equals(id)));
        }

        @Override
        public Uni<Optional<IdentityProviderConfig>> findByName(String name) {
            return Uni.createFrom().item(
                    Optional.of(config).filter(c -> c.name().equals(name)));
        }

        @Override
        public Uni<List<IdentityProviderConfig>> findEnabled() {
            return Uni.createFrom().item(config.isEnabled() ? List.of(config) : List.of());
        }

        @Override
        public Uni<Void> save(IdentityProviderConfig config) {
            return Uni.createFrom().voidItem();
        }
    }

    /** Returns the pre-staged identity instead of talking to a real provider. */
    static class FakeProviderPort implements FederatedIdentityProviderPort {
        FederatedIdentity identity;

        @Override
        public Uni<String> authorizationUrl(IdentityProviderConfig config,
                                            FederationAuthorizationRequest request) {
            return Uni.createFrom().item("https://idp.example/authorize?state=" + request.state());
        }

        @Override
        public Uni<FederatedIdentity> exchangeCode(IdentityProviderConfig config, FederationCallback callback) {
            return Uni.createFrom().item(identity);
        }
    }

    /** Federation links keyed by provider + subject, like the real repository. */
    static class InMemoryFederatedIdentities implements FederatedIdentityRepository {
        private final ConcurrentHashMap<String, FederatedIdentityLink> links = new ConcurrentHashMap<>();

        @Override
        public Uni<Optional<FederatedIdentityLink>> findByProviderAndSubject(UUID providerId, String subject) {
            return Uni.createFrom().item(Optional.ofNullable(links.get(providerId + "|" + subject)));
        }

        @Override
        public Uni<Void> save(FederatedIdentityLink link) {
            links.put(link.providerId() + "|" + link.subject(), link);
            return Uni.createFrom().voidItem();
        }
    }
}
