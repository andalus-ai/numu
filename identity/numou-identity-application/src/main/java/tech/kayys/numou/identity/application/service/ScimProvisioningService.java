package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.identity.application.dto.FederationSettings;
import tech.kayys.numou.identity.application.dto.GrantRoleAssignmentRequest;
import tech.kayys.numou.identity.application.dto.ScimGroup;
import tech.kayys.numou.identity.application.dto.ScimGroupMember;
import tech.kayys.numou.identity.application.dto.ScimPatchOperation;
import tech.kayys.numou.identity.application.dto.ScimPatchRequest;
import tech.kayys.numou.identity.application.dto.ScimUser;
import tech.kayys.numou.identity.application.dto.ScimUserWrite;
import tech.kayys.numou.identity.application.port.FederatedIdentityRepository;
import tech.kayys.numou.identity.application.port.FederationSettingsPort;
import tech.kayys.numou.identity.application.port.IdentityProviderConfigRepository;
import tech.kayys.numou.identity.application.port.PasswordHasher;
import tech.kayys.numou.identity.application.port.ScopedRoleAssignmentRepository;
import tech.kayys.numou.identity.application.port.UserRepository;
import tech.kayys.numou.identity.domain.idp.FederatedIdentityLink;
import tech.kayys.numou.identity.domain.idp.IdentityProviderConfig;
import tech.kayys.numou.identity.domain.idp.IdentityProviderConfigId;
import tech.kayys.numou.identity.domain.role.RoleId;
import tech.kayys.numou.identity.domain.user.User;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.identity.domain.valueobject.DisplayName;
import tech.kayys.numou.identity.domain.valueobject.EmailAddress;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * SCIM 2.0 automated lifecycle management (RFC 7644 subset): the enterprise IdP becomes the
 * system of record, and every joiner/mover/leaver event flows through this service.
 *
 * <p>Identity resolution order for a SCIM User write: (1) existing federation link
 * ({@code providerId + externalId}), (2) existing local user with the same e-mail (adopted and
 * linked), (3) fresh JIT-provisioned user. Deprovisioning a user ({@code active=false} or DELETE)
 * deactivates the account and removes all its scoped role assignments; reactivation is supported.
 *
 * <p>Groups are virtual: every {@code groupRoleMappings} entry of the linked provider config is
 * one SCIM Group whose members are the principals holding that role grant at the configured scope,
 * so Okta/Entra push-membership writes land on exactly the same grants interactive JIT logins use.
 */
public class ScimProvisioningService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final IdentityProviderConfigRepository providerConfigs;
    private final FederationSettingsPort settingsPort;
    private final FederatedIdentityRepository federatedIdentities;
    private final UserRepository userRepository;
    private final ScopedRoleAssignmentService roleAssignments;
    private final ScopedRoleAssignmentRepository roleAssignmentRepository;
    private final PasswordHasher passwordHasher;
    private final EventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;
    private final DomainClock clock;

    public ScimProvisioningService(
            IdentityProviderConfigRepository providerConfigs,
            FederationSettingsPort settingsPort,
            FederatedIdentityRepository federatedIdentities,
            UserRepository userRepository,
            ScopedRoleAssignmentService roleAssignments,
            ScopedRoleAssignmentRepository roleAssignmentRepository,
            PasswordHasher passwordHasher,
            EventPublisher eventPublisher,
            UnitOfWork unitOfWork,
            DomainClock clock
    ) {
        this.providerConfigs = Objects.requireNonNull(providerConfigs);
        this.settingsPort = Objects.requireNonNull(settingsPort);
        this.federatedIdentities = Objects.requireNonNull(federatedIdentities);
        this.userRepository = Objects.requireNonNull(userRepository);
        this.roleAssignments = Objects.requireNonNull(roleAssignments);
        this.roleAssignmentRepository = Objects.requireNonNull(roleAssignmentRepository);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.clock = Objects.requireNonNull(clock);
    }

    // ------------------------------------------------------------------ users

    /**
     * Creates a user or adopts an existing one - idempotent on the provider's
     * {@code externalId} first, then on the e-mail address.
     *
     * @param providerId the SCIM-linked provider config (any enabled type)
     */
    public Uni<ScimUser> createOrAdoptUser(UUID providerId, ScimUserWrite write) {
        Objects.requireNonNull(providerId, "providerId cannot be null");
        Objects.requireNonNull(write, "write cannot be null");
        EmailAddress email = emailOf(write);
        return providerSettings(providerId)
                .chain(settings -> unitOfWork.execute(() ->
                        resolveForWrite(providerId, write, email)
                                .chain(resolved -> applyUserState(
                                        providerId, resolved, write, email))
                ));
    }

    /** Replaces a user (PUT semantics). */
    public Uni<ScimUser> replaceUser(UUID providerId, UUID userId, ScimUserWrite write) {
        Objects.requireNonNull(providerId, "providerId cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(write, "write cannot be null");
        EmailAddress email = emailOf(write);
        return providerSettings(providerId)
                .chain(ignored -> unitOfWork.execute(() ->
                        loadUser(userId)
                                .chain(user -> {
                                    if (!user.email().equals(email)) {
                                        user.changeEmail(email, clock.now());
                                    }
                                    return applyUserState(providerId,
                                            new ScimTarget(user, null), write, email);
                                })
                ));
    }

    /**
     * Applies an explicit PATCH operation list: {@code active} flips, {@code userName} renames,
     * {@code displayName} renames. Unknown paths fail closed.
     */
    public Uni<ScimUser> patchUser(
            UUID providerId, UUID userId, List<ScimPatchOperation> operations) {
        Objects.requireNonNull(providerId, "providerId cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(operations, "operations cannot be null");
        return providerSettings(providerId)
                .chain(ignored -> unitOfWork.execute(() ->
                        loadUser(userId)
                                .chain(user -> {
                                    for (ScimPatchOperation operation : operations) {
                                        applyPatchOperation(user, operation);
                                    }
                                    return persistUser(user);
                                })
                ));
    }

    /** Reads one user back as a SCIM resource (for GET, and for create/update responses). */
    public Uni<ScimUser> getUser(UUID providerId, UUID userId) {
        return unitOfWork.execute(() ->
                loadUser(userId)
                        .chain(user -> federatedIdentities.findByUserId(userId)
                                .map(linkOpt -> toScimUser(user, linkOpt.orElse(null))))
        );
    }

    /**
     * Deprovisions a user (DELETE): deactivates the account, whose deactivation event
     * fans out through the outbox like every other state transition.
     */
    public Uni<Void> deprovisionUser(UUID providerId, UUID userId) {
        Objects.requireNonNull(providerId, "providerId cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
        return providerSettings(providerId)
                .chain(ignored -> unitOfWork.execute(() ->
                        loadUser(userId)
                                .chain(user -> {
                                    if (user.isActive()) {
                                        user.deactivate("scim-deprovision", clock.now());
                                    }
                                    return userRepository.save(user)
                                            // Revoke every scoped role assignment held by the user
                                            // so a deactivated SCIM user retains no effective permissions.
                                            .chain(ignored2 -> roleAssignmentRepository.deleteByPrincipal("USER", userId))
                                            .chain(() -> eventPublisher.publish(user.pullDomainEvents()));
                                })
                ));
    }

    // ------------------------------------------------------------------ internals: users

    /**
     * SCIM User write resolution: existing link, else existing e-mail (adopted), else nothing yet
     * (the caller provisions fresh). Never creates anything on its own.
     */
    private Uni<ScimTarget> resolveForWrite(UUID providerId, ScimUserWrite write, EmailAddress email) {
        if (write.externalId() != null) {
            return federatedIdentities.findByProviderAndSubject(providerId, write.externalId())
                    .chain(linkOpt -> {
                        if (linkOpt.isPresent()) {
                            return loadUser(UserId.of(linkOpt.get().userId()))
                                    .map(user -> new ScimTarget(user, linkOpt.get()));
                        }
                        return userRepository.findByEmail(email)
                                .map(userOpt -> userOpt
                                        .map(user -> new ScimTarget(user, null))
                                        .orElseGet(() -> new ScimTarget(null, null)));
                    });
        }
        return userRepository.findByEmail(email)
                .map(userOpt -> userOpt
                        .map(user -> new ScimTarget(user, null))
                        .orElseGet(() -> new ScimTarget(null, null)));
    }

    /**
     * Applies the desired state to a resolved target: renames/emails when they differ, links the
     * {@code externalId}, flips active, and provisions when the target is new.
     */
    private Uni<ScimUser> applyUserState(
            UUID providerId, ScimTarget target, ScimUserWrite write, EmailAddress email) {
        if (target.user() == null) {
            return provisionScimUser(providerId, write, email);
        }
        var user = target.user();
        if (!user.email().equals(email)) {
            user.changeEmail(email, clock.now());
        }
        if (write.displayName() != null) {
            user.rename(DisplayName.of(write.displayName()), clock.now());
        }

        Uni<FederatedIdentityLink> linkUni = target.link() != null
                ? Uni.createFrom().item(target.link())
                : (write.externalId() != null
                        ? saveLink(providerId, write.externalId(), user.id().value(), email.value())
                        : Uni.createFrom().nullItem());

        return linkUni
                .chain(ignored -> setActiveFlag(user, write.active(), "scim-deprovision"))
                .chain(() -> persistUser(user));
    }

    /** Fresh provisioned user for a SCIM create - same activation rules as interactive JIT. */
    private Uni<ScimUser> provisionScimUser(UUID providerId, ScimUserWrite write, EmailAddress email) {
        String displayNameValue = write.displayName() != null
                ? write.displayName()
                : email.value().substring(0, email.value().indexOf('@'));
        var user = User.register(
                UserId.newId(),
                email,
                passwordHasher.hash(randomBase64Url(32)),
                DisplayName.of(displayNameValue),
                clock.now()
        );
        user.activate(clock.now());

        Uni<Void> linkUni = write.externalId() != null
                ? saveLink(providerId, write.externalId(), user.id().value(), email.value())
                        .onItem().ignore().andContinueWithNull()
                : Uni.createFrom().voidItem();

        return linkUni
                .chain(() -> userRepository.save(user))
                .chain(() -> eventPublisher.publish(user.pullDomainEvents()))
                .chain(() -> setActiveFlag(user, write.active(), "scim-provision"))
                .chain(() -> persistUser(user));
    }

    /** Flips the active flag exactly when it differs. Returns normally (no response mapping). */
    private Uni<Void> setActiveFlag(User user, boolean desiredActive, String reason) {
        if (desiredActive == user.isActive()) {
            return Uni.createFrom().voidItem();
        }
        if (desiredActive) {
            user.activate(clock.now());
        } else {
            user.deactivate(reason, clock.now());
        }
        return Uni.createFrom().voidItem();
    }

    /** Persists one mutated user, publishes its events, and projects it back as SCIM. */
    private Uni<ScimUser> persistUser(User user) {
        return userRepository.save(user)
                .chain(() -> eventPublisher.publish(user.pullDomainEvents()))
                .chain(() -> federatedIdentities.findByUserId(user.id().value())
                        .map(linkOpt -> toScimUser(user, linkOpt.orElse(null))));
    }

    private void applyPatchOperation(User user, ScimPatchOperation operation) {
        String path = operation.path() == null ? "" : operation.path().trim();
        String rawOp = operation.op() == null ? "replace" : operation.op().trim();
        if (!rawOp.equalsIgnoreCase("replace") && !rawOp.equalsIgnoreCase("add")) {
            throw failureUnchecked("SCIM_PATCH_UNSUPPORTED",
                    "Only replace/add operations are supported, got: " + operation.op());
        }
        Object value = operation.value();
        switch (path.toLowerCase(java.util.Locale.ROOT)) {
            case "active" -> {
                if (!(value instanceof Boolean desired)) {
                    throw failureUnchecked("SCIM_PATCH_INVALID", "active must be a boolean");
                }
                setActiveFlagSync(user, desired);
            }
            case "username", "emails.value" -> {
                if (!(value instanceof String email) || email.isBlank()) {
                    throw failureUnchecked("SCIM_PATCH_INVALID", path + " must be a non-blank string");
                }
                user.changeEmail(EmailAddress.of(email.trim()), clock.now());
            }
            case "displayname", "name.formatted" -> {
                if (!(value instanceof String name) || name.isBlank()) {
                    throw failureUnchecked("SCIM_PATCH_INVALID", path + " must be a non-blank string");
                }
                user.rename(DisplayName.of(name.trim()), clock.now());
            }
            case "" -> throw failureUnchecked("SCIM_PATCH_INVALID", "PATCH path is required");
            default -> throw failureUnchecked("SCIM_PATCH_UNSUPPORTED", "Unsupported PATCH path: " + path);
        }
    }

    private void setActiveFlagSync(User user, boolean desiredActive) {
        if (desiredActive == user.isActive()) {
            return;
        }
        if (desiredActive) {
            user.activate(clock.now());
        } else {
            user.deactivate("scim-deprovision", clock.now());
        }
    }

    private Uni<User> loadUser(UUID userId) {
        return loadUser(UserId.of(userId));
    }

    private Uni<User> loadUser(UserId userId) {
        return userRepository.findById(userId)
                .chain(userOpt -> userOpt
                        .map(Uni.createFrom()::item)
                        .orElseGet(() -> failure("SCIM_USER_NOT_FOUND", "SCIM user does not exist")));
    }

    /** Loads an enabled provider of any type - SCIM rides on the IdP's config, not its protocol. */
    private Uni<IdentityProviderConfig> providerSettings(UUID providerId) {
        return providerConfigs.findById(IdentityProviderConfigId.of(providerId))
                .chain(optional -> {
                    if (optional.isEmpty()) {
                        return failure("IDENTITY_PROVIDER_NOT_FOUND", "Identity provider not found");
                    }
                    var config = optional.get();
                    if (!config.isEnabled()) {
                        return failure("IDENTITY_PROVIDER_DISABLED", "Identity provider is disabled");
                    }
                    return Uni.createFrom().item(config);
                });
    }

    private static EmailAddress emailOf(ScimUserWrite write) {
        String raw = write.email() != null ? write.email() : write.userName();
        try {
            return EmailAddress.of(raw);
        } catch (RuntimeException ex) {
            throw failureUnchecked("SCIM_EMAIL_INVALID", "SCIM user e-mail is invalid: " + raw);
        }
    }

    private ScimUser toScimUser(User user, FederatedIdentityLink link) {
        // The User aggregate carries no timestamps; createdAt/updatedAt stay null until the
        // aggregate gains its own audit metadata. Link state (last login) is available on demand.
        return new ScimUser(
                user.id().value(),
                user.email().value(),
                link != null ? link.subject() : null,
                user.displayName().value(),
                user.email().value(),
                user.isActive(),
                null,
                null
        );
    }

    // ------------------------------------------------------------------ groups

    /**
     * Lists the virtual SCIM groups of one provider: one group per {@code groupRoleMappings}
     * entry, with the principals currently holding that grant as members. The scope comes from
     * the provider's {@code defaultScopePath}; SCIM callers cannot move a group's scope.
     */
    public Uni<List<ScimGroup>> listGroups(UUID providerId) {
        Objects.requireNonNull(providerId, "providerId cannot be null");
        return providerSettings(providerId)
                .chain(config -> {
                    FederationSettings settings = settingsPort.parse(config);
                    if (settings.groupRoleMappings().isEmpty() || settings.defaultScopePath().isBlank()) {
                        return Uni.createFrom().item(List.<ScimGroup>of());
                    }
                    List<Uni<ScimGroup>> lookups = settings.groupRoleMappings().entrySet().stream()
                            .map(entry -> groupWithMembers(entry.getKey(), entry.getValue(),
                                    settings.defaultScopePath()))
                            .toList();
                    return Uni.combine().all().unis(lookups)
                            .with(ScimGroup.class, groups -> groups);
                });
    }

    /**
     * Replaces one virtual group's membership: grants the mapped role to every listed user that
     * lacks it and removes it from every current holder not listed. Members arrive as internal
     * user ids - the REST layer resolves SCIM ids to those before calling.
     */
    public Uni<ScimGroup> replaceGroupMembers(UUID providerId, String groupName, List<UUID> memberUserIds) {
        Objects.requireNonNull(providerId, "providerId cannot be null");
        Objects.requireNonNull(groupName, "groupName cannot be null");
        Objects.requireNonNull(memberUserIds, "memberUserIds cannot be null");
        return providerSettings(providerId)
                .chain(config -> {
                    FederationSettings settings = settingsPort.parse(config);
                    String roleIdValue = settings.groupRoleMappings().get(groupName);
                    if (roleIdValue == null) {
                        return failure("SCIM_GROUP_UNKNOWN",
                                "No groupRoleMappings entry for SCIM group '" + groupName + "'");
                    }
                    if (settings.defaultScopePath().isBlank()) {
                        return failure("FEDERATION_MAPPING_MISCONFIGURED",
                                "groupRoleMappings requires defaultScopePath in the provider connection config");
                    }
                    final RoleId roleId;
                    try {
                        roleId = new RoleId(UUID.fromString(roleIdValue));
                    } catch (RuntimeException ex) {
                        return failure("FEDERATION_MAPPING_MISCONFIGURED",
                                "groupRoleMappings contains a non-UUID role id");
                    }
                    String scopePath = settings.defaultScopePath();
                    String scopeType = settings.defaultScopeType();

                    return unitOfWork.execute(() ->
                            roleAssignmentRepository.findHolders("USER", roleId, scopePath)
                                    .chain(holders -> {
                                        var current = holders.stream()
                                                .map(holder -> holder.principalId())
                                                .collect(java.util.stream.Collectors.toSet());
                                        var desired = new java.util.LinkedHashSet<>(memberUserIds);

                                        Uni<Void> pipeline = Uni.createFrom().voidItem();
                                        for (UUID userId : desired) {
                                            if (!current.contains(userId)) {
                                                pipeline = pipeline.chain(() -> roleAssignments.grant(
                                                        new GrantRoleAssignmentRequest(
                                                                "USER", userId, roleId.value(),
                                                                scopeType, scopePath, null),
                                                        null)
                                                        .onItem().ignore().andContinueWithNull());
                                            }
                                        }
                                        for (UUID holderId : current) {
                                            if (!desired.contains(holderId)) {
                                                pipeline = pipeline.chain(() ->
                                                        roleAssignmentRepository.deleteByPrincipalAndRole(
                                                                        "USER", holderId, roleId, scopePath)
                                                                .onItem().ignore().andContinueWithNull());
                                            }
                                        }
                                        return pipeline;
                                    })
                                    .chain(() -> groupWithMembers(groupName, roleIdValue, scopePath))
                    );
                });
    }

    private Uni<ScimGroup> groupWithMembers(String groupName, String roleIdValue, String scopePath) {
        final RoleId roleId;
        try {
            roleId = new RoleId(UUID.fromString(roleIdValue));
        } catch (RuntimeException ex) {
            return failure("FEDERATION_MAPPING_MISCONFIGURED",
                    "groupRoleMappings contains a non-UUID role id");
        }
        return roleAssignmentRepository.findHolders("USER", roleId, scopePath)
                .chain(holders -> {
                    List<Uni<ScimGroupMember>> memberLookups = holders.stream()
                            .map(holder -> loadUser(holder.principalId())
                                    .map(user -> new ScimGroupMember(
                                            user.id().value(), user.displayName().value()))
                                    // A holder whose user row vanished: skip, never fail the listing.
                                    .onFailure().recoverWithNull())
                            .toList();
                    return Uni.combine().all().unis(memberLookups)
                            .with(ScimGroupMember.class, members -> new ScimGroup(
                                    groupName, groupName, roleId.value(), scopePath,
                                    members.stream()
                                            .filter(member -> member != null)
                                            .toList()));
                });
    }

    private Uni<FederatedIdentityLink> saveLink(UUID providerId, String subject, UUID userId, String email) {
        var link = FederatedIdentityLink.create(providerId, subject, userId, email, clock.now());
        return federatedIdentities.save(link).replaceWith(link);
    }

    private static RuntimeException failureUnchecked(String code, String message) {
        return ApplicationError.of(code, message).toException();
    }

    private static <T> Uni<T> failure(String code, String message) {
        return Uni.createFrom().failure(failureUnchecked(code, message));
    }

    /** One SCIM write target: the user (null when nothing matches yet) and its link (may be null). */
    private record ScimTarget(User user, FederatedIdentityLink link) { }

    private static String randomBase64Url(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }
}
