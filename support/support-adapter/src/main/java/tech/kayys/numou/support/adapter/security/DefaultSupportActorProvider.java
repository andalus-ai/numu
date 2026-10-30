package tech.kayys.numou.support.adapter.security;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import org.eclipse.microprofile.jwt.JsonWebToken;
import tech.kayys.numou.ecosystem.application.port.PartyIdentityAssociationRepository;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.application.port.CurrentPrincipalPort;
import tech.kayys.numou.identity.application.port.EffectivePermissionPort;
import tech.kayys.numou.identity.application.port.MembershipManagementPort;
import tech.kayys.numou.identity.application.security.Principal;
import tech.kayys.numou.identity.application.security.PrincipalType;
import tech.kayys.numou.security.adapter.CurrentSecurity;
import tech.kayys.numou.support.application.api.ActorContext;
import tech.kayys.numou.support.application.api.ActorType;
import tech.kayys.numou.support.application.api.SupportActorProvider;
import tech.kayys.numou.support.application.api.SupportPartyContext;
import tech.kayys.numou.support.application.port.SupportPartyParticipantResolver;
import tech.kayys.numou.support.domain.ticket.RequesterRole;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves Support actors only from verified request and Identity/Ecosystem records.
 *
 * Authentication and tenant resolution are supplied by the shared security adapter.
 * Identity independently confirms an active user membership. Scoped permissions
 * determine staff capabilities. Portal actors additionally require an active
 * issuer/subject-to-Party association and a tenant-verified Party/Participant mapping.
 */
@ApplicationScoped
public class DefaultSupportActorProvider implements SupportActorProvider {
    public static final String PERMISSION_MANAGE = "support.ticket.manage";
    public static final String PERMISSION_ADMIN = "support.ticket.admin";
    public static final String PERMISSION_RESOLVE = "support.ticket.resolve";

    private final CurrentPrincipalPort principals;
    private final MembershipManagementPort memberships;
    private final EffectivePermissionPort permissions;
    private final PartyIdentityAssociationRepository associations;
    private final CurrentSecurity currentSecurity;
    private final JsonWebToken token;
    private final SupportPartyParticipantResolver partyParticipants;

    @Inject
    public DefaultSupportActorProvider(
            CurrentPrincipalPort principals,
            MembershipManagementPort memberships,
            EffectivePermissionPort permissions,
            PartyIdentityAssociationRepository associations,
            CurrentSecurity currentSecurity,
            JsonWebToken token,
            Instance<SupportPartyParticipantResolver> partyParticipants) {
        this(principals, memberships, permissions, associations, currentSecurity, token,
                partyParticipants != null && partyParticipants.isResolvable() ? partyParticipants.get() : null);
    }

    public DefaultSupportActorProvider(
            CurrentPrincipalPort principals,
            MembershipManagementPort memberships,
            EffectivePermissionPort permissions,
            PartyIdentityAssociationRepository associations,
            CurrentSecurity currentSecurity,
            JsonWebToken token,
            SupportPartyParticipantResolver partyParticipants) {
        this.principals = Objects.requireNonNull(principals, "principals");
        this.memberships = Objects.requireNonNull(memberships, "memberships");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.associations = Objects.requireNonNull(associations, "associations");
        this.currentSecurity = Objects.requireNonNull(currentSecurity, "currentSecurity");
        this.token = Objects.requireNonNull(token, "token");
        this.partyParticipants = partyParticipants;
    }

    @Override
    public Uni<ActorContext> currentActor() {
        return principals.current().chain(this::resolveAuthenticatedUser);
    }

    private Uni<ActorContext> resolveAuthenticatedUser(Principal principal) {
        if (principal == null || !principal.authenticated()
                || principal.type() != PrincipalType.USER || principal.userId() == null) {
            return denied();
        }

        final TenantId tenant;
        try {
            var verified = currentSecurity.require();
            tenant = TenantId.of(verified.tenantId());
            // The two independently supplied identity views must refer to the same user.
            // This also rejects service principals and subject-derived fallback IDs.
            if (!verified.principal().id().value().equals(principal.userId().value())
                    || !Objects.equals(token.getSubject(), principal.subject())) {
                return denied();
            }
        } catch (RuntimeException invalidContext) {
            return denied();
        }

        return memberships.isActive(tenant, principal.userId()).chain(active -> {
            if (!Boolean.TRUE.equals(active)) {
                return denied();
            }
            return permissions.permissionsFor(tenant, principal.userId())
                    .chain(grants -> resolveSupportRole(tenant, principal, grants));
        });
    }

    private Uni<ActorContext> resolveSupportRole(TenantId tenant, Principal principal, Set<String> grants) {
        Set<String> effective = grants == null ? Set.of() : Set.copyOf(grants);
        if (hasAny(effective, PERMISSION_MANAGE, PERMISSION_ADMIN)) {
            return Uni.createFrom().item(new ActorContext(tenant, ActorType.MANAGER, principal.userId(), null));
        }
        if (hasAny(effective, PERMISSION_RESOLVE)) {
            return Uni.createFrom().item(new ActorContext(tenant, ActorType.AGENT, principal.userId(), null));
        }

        String issuer = token.getIssuer();
        String subject = principal.subject();
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            return denied();
        }
        return associations.findActiveByTenantAndIdentity(tenant, new IdentitySubjectRef(issuer, subject))
                .chain(link -> {
                    if (link == null || link.isEmpty() || partyParticipants == null) {
                        return denied();
                    }
                    return partyParticipants.resolve(tenant, link.get().partyId())
                            .chain(resolved -> portalActor(tenant, principal, resolved));
                });
    }

    private Uni<ActorContext> portalActor(
            TenantId tenant, Principal principal, Optional<SupportPartyContext> resolved) {
        if (resolved == null || resolved.isEmpty()) {
            return denied();
        }
        SupportPartyContext party = resolved.get();
        if (party.requesterRole() == RequesterRole.CUSTOMER) {
            return Uni.createFrom().item(new ActorContext(
                    tenant, ActorType.CUSTOMER, principal.userId(), party.participantId()));
        }
        if (party.requesterRole() == RequesterRole.PARTNER) {
            return Uni.createFrom().item(new ActorContext(
                    tenant, ActorType.PARTNER, principal.userId(), party.participantId()));
        }
        return denied();
    }

    private static boolean hasAny(Set<String> grants, String... required) {
        for (String permission : required) {
            if (grants.contains(permission)) {
                return true;
            }
        }
        return false;
    }

    private static <T> Uni<T> denied() {
        return Uni.createFrom().failure(new ForbiddenException("A verified Support actor is required"));
    }
}
