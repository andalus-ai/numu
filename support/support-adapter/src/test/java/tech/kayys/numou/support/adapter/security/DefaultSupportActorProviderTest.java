package tech.kayys.numou.support.adapter.security;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.application.port.PartyIdentityAssociationRepository;
import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.party.PartyIdentityAssociation;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.application.port.CurrentPrincipalPort;
import tech.kayys.numou.identity.application.port.EffectivePermissionPort;
import tech.kayys.numou.identity.application.port.MembershipManagementPort;
import tech.kayys.numou.identity.application.security.Principal;
import tech.kayys.numou.identity.application.security.PrincipalType;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.security.adapter.CurrentSecurity;
import tech.kayys.numou.security.application.context.SecurityContext;
import tech.kayys.numou.security.domain.identifier.PrincipalId;
import tech.kayys.numou.support.application.api.ActorType;
import tech.kayys.numou.support.application.api.SupportPartyContext;
import tech.kayys.numou.support.application.port.SupportPartyParticipantResolver;
import tech.kayys.numou.support.domain.ticket.RequesterRole;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DefaultSupportActorProviderTest {
    private static final String ISSUER = "https://identity.example";
    private static final String SUBJECT = "user-subject-123";

    @Test
    void activeTenantMemberWithSupportResolvePermissionBecomesAgent() {
        Fixture fixture = new Fixture(Set.of(DefaultSupportActorProvider.PERMISSION_RESOLVE), true,
                false, null, null, null);

        var actor = fixture.provider.currentActor().await().indefinitely();

        assertEquals(ActorType.AGENT, actor.type());
        assertEquals(fixture.tenant, actor.tenantId());
        assertEquals(fixture.user, actor.userId());
        assertNull(actor.participantId());
    }

    @Test
    void portalActorRequiresActivePartyLinkAndTenantVerifiedParticipantMapping() {
        PartyId partyId = PartyId.generate();
        ParticipantId participantId = ParticipantId.generate();
        Fixture fixture = new Fixture(Set.of(), true, true, partyId, participantId, RequesterRole.CUSTOMER);

        var actor = fixture.provider.currentActor().await().indefinitely();

        assertEquals(ActorType.CUSTOMER, actor.type());
        assertEquals(fixture.tenant, actor.tenantId());
        assertEquals(participantId, actor.participantId());
        assertEquals(fixture.user, actor.userId());
    }

    @Test
    void replyOnlyPermissionDoesNotGrantAgentCapabilities() {
        Fixture fixture = new Fixture(Set.of("support.ticket.reply"), true,
                false, null, null, null);
        assertThrows(RuntimeException.class, () -> fixture.provider.currentActor().await().indefinitely());
    }

    @Test
    void inactiveMembershipIsDeniedBeforeAnySupportRoleIsResolved() {
        Fixture fixture = new Fixture(Set.of(DefaultSupportActorProvider.PERMISSION_ADMIN), false,
                false, null, null, null);
        assertThrows(RuntimeException.class, () -> fixture.provider.currentActor().await().indefinitely());
    }

    @Test
    void rolePermissionWithoutMatchingVerifiedUserContextIsDenied() throws Exception {
        Fixture fixture = new Fixture(Set.of(DefaultSupportActorProvider.PERMISSION_ADMIN), true,
                false, null, null, null);
        var wrongContext = new SecurityContext(
                new tech.kayys.numou.security.domain.authorization.Principal(
                        PrincipalId.of(UUID.randomUUID()), "different-user", Set.of(fixture.tenant)),
                fixture.tenant.value(), Set.of(), "");
        setCurrentSecurity(fixture.currentSecurity, wrongContext);
        assertThrows(RuntimeException.class, () -> fixture.provider.currentActor().await().indefinitely());
    }

    private static void setCurrentSecurity(CurrentSecurity current, SecurityContext context) throws Exception {
        Field field = CurrentSecurity.class.getDeclaredField("context");
        field.setAccessible(true);
        field.set(current, context);
    }

    private static JsonWebToken token() {
        return (JsonWebToken) Proxy.newProxyInstance(JsonWebToken.class.getClassLoader(),
                new Class<?>[]{JsonWebToken.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getIssuer" -> ISSUER;
                    case "getSubject" -> SUBJECT;
                    case "getClaim" -> null;
                    case "getGroups" -> Set.of();
                    case "getName" -> SUBJECT;
                    case "toString" -> "TestJsonWebToken";
                    default -> null;
                });
    }

    private static final class Fixture {
        final TenantId tenant = TenantId.generate();
        final UserId user = UserId.newId();
        final CurrentSecurity currentSecurity = new CurrentSecurity();
        final DefaultSupportActorProvider provider;

        Fixture(Set<String> grants, boolean activeMembership, boolean hasPartyLink,
                PartyId partyId, ParticipantId participantId, RequesterRole requesterRole) {
            var securityPrincipal = new tech.kayys.numou.security.domain.authorization.Principal(
                    PrincipalId.of(user.value()), "test-user", Set.of(tenant));
            try {
                setCurrentSecurity(currentSecurity,
                        new SecurityContext(securityPrincipal, tenant.value(), Set.of(), ""));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }

            CurrentPrincipalPort principalPort = () -> Uni.createFrom().item(new Principal(
                    user, SUBJECT, "test-user", "Test User", Set.of(), Map.of(), PrincipalType.USER));
            MembershipManagementPort membershipPort = (requestedTenant, requestedUser) ->
                    Uni.createFrom().item(activeMembership && tenant.equals(requestedTenant) && user.equals(requestedUser));
            EffectivePermissionPort permissionPort = (requestedTenant, requestedUser) ->
                    Uni.createFrom().item(tenant.equals(requestedTenant) && user.equals(requestedUser)
                            ? grants : Set.of());

            var activeLink = hasPartyLink
                    ? PartyIdentityAssociation.link(UUID.randomUUID(), tenant, partyId,
                        new IdentitySubjectRef(ISSUER, SUBJECT), Instant.parse("2026-10-30T00:00:00Z"))
                    : null;
            PartyIdentityAssociationRepository associationRepository = new PartyIdentityAssociationRepository() {
                @Override
                public Uni<PartyIdentityAssociation> save(PartyIdentityAssociation association) {
                    return Uni.createFrom().item(association);
                }

                @Override
                public Uni<java.util.Optional<PartyIdentityAssociation>> findActiveByTenantAndIdentity(
                        TenantId requestedTenant, IdentitySubjectRef identity) {
                    if (!tenant.equals(requestedTenant) || activeLink == null
                            || !activeLink.identity().equals(identity)) {
                        return Uni.createFrom().item(java.util.Optional.empty());
                    }
                    return Uni.createFrom().item(java.util.Optional.of(activeLink));
                }

                @Override
                public Uni<List<PartyIdentityAssociation>> findByTenantAndParty(TenantId requestedTenant, PartyId requestedParty) {
                    return Uni.createFrom().item(List.of());
                }
            };

            SupportPartyParticipantResolver participantResolver = hasPartyLink
                    ? (requestedTenant, requestedParty) -> Uni.createFrom().item(
                        tenant.equals(requestedTenant) && partyId.equals(requestedParty)
                                ? java.util.Optional.of(new SupportPartyContext(participantId, requesterRole))
                                : java.util.Optional.empty())
                    : null;

            this.provider = new DefaultSupportActorProvider(principalPort, membershipPort, permissionPort,
                    associationRepository, currentSecurity, token(), participantResolver);
        }
    }
}
