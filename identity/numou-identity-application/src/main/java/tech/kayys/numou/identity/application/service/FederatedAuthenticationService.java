package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.identity.application.dto.FederationAuthorizationRequest;
import tech.kayys.numou.identity.application.dto.FederationCallback;
import tech.kayys.numou.identity.application.dto.FederationSettings;
import tech.kayys.numou.identity.application.dto.FederatedAuthorizationStart;
import tech.kayys.numou.identity.application.dto.FederatedLoginResponse;
import tech.kayys.numou.identity.application.dto.FederatedPrincipal;
import tech.kayys.numou.identity.application.dto.GrantRoleAssignmentRequest;
import tech.kayys.numou.identity.application.port.FederatedIdentityProviderPort;
import tech.kayys.numou.identity.application.port.FederatedIdentityRepository;
import tech.kayys.numou.identity.application.port.FederationSettingsPort;
import tech.kayys.numou.identity.application.port.IdentityProviderConfigRepository;
import tech.kayys.numou.identity.application.port.PasswordHasher;
import tech.kayys.numou.identity.application.port.UserRepository;
import tech.kayys.numou.identity.domain.idp.FederatedIdentityLink;
import tech.kayys.numou.identity.domain.idp.IdentityProviderConfig;
import tech.kayys.numou.identity.domain.idp.IdentityProviderConfigId;
import tech.kayys.numou.identity.domain.idp.IdentityProviderType;
import tech.kayys.numou.identity.domain.role.assignment.RoleScopeType;
import tech.kayys.numou.identity.domain.user.FederatedIdentity;
import tech.kayys.numou.identity.domain.user.User;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.identity.domain.valueobject.DisplayName;
import tech.kayys.numou.identity.domain.valueobject.EmailAddress;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Federated login use cases: starts an authorization flow (PKCE state/nonce) and completes it
 * with Just-In-Time provisioning plus group-to-role synchronization.
 *
 * <p>Everything protocol-specific (discovery, code exchange, signature verification) lives in the
 * {@link FederatedIdentityProviderPort} adapter; this class owns only the identity decisions:
 * who the internal user is, whether they may sign in, and which roles their IdP groups map to.
 */
public class FederatedAuthenticationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final IdentityProviderConfigRepository providerConfigs;
    private final FederatedIdentityProviderPort federationPort;
    private final FederationSettingsPort settingsPort;
    private final FederatedIdentityRepository federatedIdentities;
    private final UserRepository userRepository;
    private final ScopedRoleAssignmentService roleAssignments;
    private final PasswordHasher passwordHasher;
    private final EventPublisher eventPublisher;
    private final TokenService tokenService;
    private final UnitOfWork unitOfWork;
    private final DomainClock clock;

    public FederatedAuthenticationService(
            IdentityProviderConfigRepository providerConfigs,
            FederatedIdentityProviderPort federationPort,
            FederationSettingsPort settingsPort,
            FederatedIdentityRepository federatedIdentities,
            UserRepository userRepository,
            ScopedRoleAssignmentService roleAssignments,
            PasswordHasher passwordHasher,
            EventPublisher eventPublisher,
            TokenService tokenService,
            UnitOfWork unitOfWork,
            DomainClock clock
    ) {
        this.providerConfigs = Objects.requireNonNull(providerConfigs);
        this.federationPort = Objects.requireNonNull(federationPort);
        this.settingsPort = Objects.requireNonNull(settingsPort);
        this.federatedIdentities = Objects.requireNonNull(federatedIdentities);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.roleAssignments = Objects.requireNonNull(roleAssignments);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.tokenService = Objects.requireNonNull(tokenService);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Starts a federated login: loads the provider, generates state/nonce/PKCE material and
     * returns the URL the browser must be redirected to. The caller persists
     * {@code state/nonce/codeVerifier} in an HttpOnly cookie until the callback arrives.
     */
    public Uni<FederatedAuthorizationStart> begin(UUID providerId) {
        Objects.requireNonNull(providerId, "providerId cannot be null");
        return loadProvider(providerId)
                .chain(config -> {
                    String state = randomBase64Url(32);
                    String nonce = randomBase64Url(16);
                    String codeVerifier = randomBase64Url(48);
                    var request = new FederationAuthorizationRequest(state, nonce, pkceChallenge(codeVerifier));
                    return federationPort.authorizationUrl(config, request)
                            .map(url -> new FederatedAuthorizationStart(state, nonce, codeVerifier, url));
                });
    }

    /**
     * Completes a federated login: exchanges the code, resolves or provisions the internal user,
     * synchronizes group-derived role assignments and issues an internal Numou access token.
     *
     * <p>State/CSRF validation (callback state vs the browser cookie) is the resource's job;
     * this method trusts a callback that has already been bound to a started flow.
     */
    public Uni<FederatedLoginResponse> complete(UUID providerId, FederationCallback callback) {
        Objects.requireNonNull(providerId, "providerId cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");

        return loadProvider(providerId)
                .chain(config -> federationPort.exchangeCode(config, callback)
                        .chain(identity -> authenticate(config, identity)));
    }

    /**
     * Shared JIT entry point for the other federation flows (LDAP bind, SAML assertion):
     * resolves or provisions the internal user, synchronizes group-derived role assignments
     * and issues an internal Numou access token. Protocol verification (bind success, signature
     * validity) has already happened in the adapter before this is called.
     */
    Uni<FederatedLoginResponse> authenticate(IdentityProviderConfig config, FederatedIdentity identity) {
        Objects.requireNonNull(config, "config cannot be null");
        Objects.requireNonNull(identity, "identity cannot be null");
        return resolvePrincipal(config, identity)
                .map(principal -> new FederatedLoginResponse(
                        tokenService.issueFor(principal.userId(), "USER", principal.displayName()),
                        principal.provisioned(),
                        principal.userId(),
                        identity.groups()
                ));
    }

    /**
     * Resolves the internal principal for a verified external identity without issuing
     * a token - used by LDAP/SAML services and by SCIM-driven linking.
     */
    Uni<FederatedPrincipal> resolvePrincipal(IdentityProviderConfig config, FederatedIdentity identity) {
        Objects.requireNonNull(config, "config cannot be null");
        Objects.requireNonNull(identity, "identity cannot be null");
        FederationSettings settings = settingsPort.parse(config);
        return unitOfWork.execute(() ->
                resolveUser(config, identity)
                        .chain(resolved -> applyGroupMappings(resolved, settings, identity)
                                .replaceWith(new FederatedPrincipal(
                                        resolved.userId(), resolved.displayName(), resolved.provisioned())))
        );
    }

    /**
     * Loads an enabled provider of the expected type for one federation flow. Package-visible
     * so the LDAP and SAML services share the same not-found/disabled/type-mismatch rules.
     */
    Uni<IdentityProviderConfig> requireProvider(
            UUID providerId, IdentityProviderType expectedType, String flowName) {
        return providerConfigs.findById(IdentityProviderConfigId.of(providerId))
                .chain(optional -> {
                    if (optional.isEmpty()) {
                        return failure("IDENTITY_PROVIDER_NOT_FOUND", "Identity provider not found");
                    }
                    var config = optional.get();
                    if (!config.isEnabled()) {
                        return failure("IDENTITY_PROVIDER_DISABLED", "Identity provider is disabled");
                    }
                    if (config.type() != expectedType) {
                        return failure(
                                "FEDERATION_UNSUPPORTED_TYPE",
                                "Provider type " + config.type() + " does not use the " + flowName + " flow"
                        );
                    }
                    return Uni.createFrom().item(config);
                });
    }

    private Uni<IdentityProviderConfig> loadProvider(UUID providerId) {
        return requireProvider(providerId, IdentityProviderType.OIDC, "OIDC authorization-code");
    }

    /** Finds the link for this provider subject, or JIT-provisions a user when none exists. */
    private Uni<ResolvedUser> resolveUser(IdentityProviderConfig config, FederatedIdentity identity) {
        UUID providerId = config.id().value();
        return federatedIdentities.findByProviderAndSubject(providerId, identity.subject())
                .chain(linkOpt -> linkOpt
                        .map(this::useExistingLink)
                        .orElseGet(() -> provisionNewUser(providerId, identity)));
    }

    private Uni<ResolvedUser> useExistingLink(FederatedIdentityLink link) {
        return userRepository.findById(UserId.of(link.userId()))
                .chain(userOpt -> {
                    if (userOpt.isEmpty()) {
                        return failure("FEDERATED_USER_MISSING",
                                "Linked internal user no longer exists; remove the federation link");
                    }
                    var user = userOpt.get();
                    if (!user.isActive()) {
                        return failure("FEDERATED_USER_DISABLED", "The linked user account is not active");
                    }
                    return federatedIdentities.save(link.withLastLogin(clock.now()))
                            .replaceWith(new ResolvedUser(user.id().value(), user.displayName().value(), false));
                });
    }

    private Uni<ResolvedUser> provisionNewUser(UUID providerId, FederatedIdentity identity) {
        if (identity.email().isBlank()) {
            return failure("FEDERATED_EMAIL_REQUIRED",
                    "The identity provider did not assert an e-mail address; JIT provisioning needs one");
        }

        final EmailAddress email;
        try {
            email = EmailAddress.of(identity.email());
        } catch (RuntimeException ex) {
            return failure("FEDERATED_EMAIL_INVALID", "The identity provider asserted an invalid e-mail address");
        }

        return userRepository.findByEmail(email)
                .chain(existing -> existing.isPresent()
                        ? adoptExistingUser(providerId, identity, existing.get())
                        : createProvisionedUser(providerId, email, identity));
    }

    /** Same e-mail already registered locally: link the account instead of duplicating it. */
    private Uni<ResolvedUser> adoptExistingUser(UUID providerId, FederatedIdentity identity, User user) {
        if (!user.isActive()) {
            return failure("FEDERATED_USER_DISABLED", "The local account for this e-mail is not active");
        }
        return saveLink(providerId, identity, user.id())
                .replaceWith(new ResolvedUser(user.id().value(), user.displayName().value(), false));
    }

    private Uni<ResolvedUser> createProvisionedUser(UUID providerId, EmailAddress email, FederatedIdentity identity) {
        String displayNameValue = identity.displayName() != null
                ? identity.displayName()
                : email.value().substring(0, email.value().indexOf('@'));
        var user = User.register(
                UserId.newId(),
                email,
                // Federated users cannot use the local password path: store an unusable random hash so
                // the local credential never validates and only the IdP can authenticate this account.
                passwordHasher.hash(randomBase64Url(32)),
                DisplayName.of(displayNameValue),
                clock.now()
        );
        // The provider already verified possession of this account.
        user.activate(clock.now());

        return userRepository.save(user)
                .chain(() -> eventPublisher.publish(user.pullDomainEvents()))
                .chain(() -> saveLink(providerId, identity, user.id()))
                .replaceWith(new ResolvedUser(user.id().value(), user.displayName().value(), true));
    }

    private Uni<Void> saveLink(UUID providerId, FederatedIdentity identity, UserId userId) {
        return federatedIdentities.save(FederatedIdentityLink.create(
                providerId, identity.subject(), userId.value(), identity.email(), clock.now()
        ));
    }

    /**
     * Maps provider groups to internal role assignments. Grants are deduplicated against the
     * assignments the principal already holds, so repeated logins never pile up duplicates.
     */
    private Uni<Void> applyGroupMappings(ResolvedUser resolved, FederationSettings settings,
                                         FederatedIdentity identity) {
        return synchronizeGroupRoles(resolved.userId(), identity.groups(), settings);
    }

    /**
     * Synchronizes one principal's group-derived role assignments against the desired group set -
     * the same mapping rules interactive logins use, without a login attached. Package-visible so
     * the SCIM group use case shares the exact rule set.
     */
    Uni<Void> synchronizeGroupRoles(UUID userId, java.util.Set<String> groups, FederationSettings settings) {
        if (groups.isEmpty() || settings.groupRoleMappings().isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        if (settings.defaultScopePath().isBlank()) {
            return failure("FEDERATION_MAPPING_MISCONFIGURED",
                    "groupRoleMappings requires defaultScopePath in the provider connection config");
        }

        final RoleScopeType scopeType;
        try {
            scopeType = RoleScopeType.valueOf(settings.defaultScopeType().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            return failure("FEDERATION_MAPPING_MISCONFIGURED",
                    "defaultScopeType must be one of SYSTEM, ORGANIZATION, WORKSPACE");
        }

        final List<UUID> roleIdsToGrant = new ArrayList<>();
        return roleAssignments.listForPrincipal("USER", userId)
                .chain(existing -> {
                    settings.groupRoleMappings().entrySet().stream()
                            .filter(entry -> groups.contains(entry.getKey()))
                            .map(Map.Entry::getValue)
                            .distinct()
                            .forEach(roleId -> {
                                UUID wanted = parseRoleId(roleId);
                                if (wanted == null) {
                                    throw new IllegalStateException(
                                            "groupRoleMappings contains a non-UUID role id for group " + roleId);
                                }
                                if (existing.stream().noneMatch(a ->
                                        a.roleId().equals(wanted) && a.scopePath().equals(settings.defaultScopePath()))) {
                                    roleIdsToGrant.add(wanted);
                                }
                            });

                    Uni<Void> pipeline = Uni.createFrom().voidItem();
                    for (UUID roleId : roleIdsToGrant) {
                        pipeline = pipeline.chain(() -> roleAssignments.grant(
                                new GrantRoleAssignmentRequest(
                                        "USER",
                                        userId,
                                        roleId,
                                        scopeType.name(),
                                        settings.defaultScopePath(),
                                        null
                                ),
                                null
                        ).onItem().ignore().andContinueWithNull());
                    }
                    return pipeline;
                })
                .onFailure()
                .transform(ex -> ex instanceof IllegalStateException
                        ? ApplicationError.of(
                                "FEDERATION_MAPPING_MISCONFIGURED",
                                "groupRoleMappings must contain UUID role ids").toException()
                        : ex);
    }

    private static UUID parseRoleId(String roleId) {
        try {
            return UUID.fromString(roleId);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static <T> Uni<T> failure(String code, String message) {
        return Uni.createFrom().failure(ApplicationError.of(code, message).toException());
    }

    private record ResolvedUser(UUID userId, String displayName, boolean provisioned) { }

    private static String randomBase64Url(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    /** RFC 7636 S256: BASE64URL(SHA256(ASCII(codeVerifier))). */
    static String pkceChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
