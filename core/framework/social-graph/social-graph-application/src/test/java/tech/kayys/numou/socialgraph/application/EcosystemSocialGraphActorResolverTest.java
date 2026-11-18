package tech.kayys.numou.socialgraph.application;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.application.port.PartyIdentityAssociationRepository;
import tech.kayys.numou.ecosystem.application.port.PartyLookupPort;
import tech.kayys.numou.ecosystem.domain.model.Party;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.party.PartyIdentityAssociation;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class EcosystemSocialGraphActorResolverTest {
    private static final Instant NOW = Instant.parse("2026-11-17T00:00:00Z");

    @Test
    void resolvesOnlyTheActiveTenantScopedIdentityAndActiveParty() {
        TenantId tenant = TenantId.generate();
        PartyId partyId = PartyId.generate();
        IdentitySubjectRef subject = new IdentitySubjectRef("https://issuer.example", "subject-123");
        PartyIdentityAssociation association = PartyIdentityAssociation.link(
                UUID.randomUUID(), tenant, partyId, subject, NOW);
        Party party = Party.register(partyId, tech.kayys.numou.ecosystem.domain.party.PartyKind.PERSON);
        AtomicReference<TenantId> requestedTenant = new AtomicReference<>();
        AtomicReference<IdentitySubjectRef> requestedSubject = new AtomicReference<>();

        var resolver = new EcosystemSocialGraphActorResolver(
                associations((t, s) -> {
                    requestedTenant.set(t);
                    requestedSubject.set(s);
                    return Optional.of(association);
                }),
                partyLookup(id -> Optional.of(party)));

        Optional<SocialGraphActor> actor = resolver.resolve(tenant, subject).await().indefinitely();

        assertThat(actor).contains(new SocialGraphActor(tenant, partyId));
        assertThat(requestedTenant).hasValue(tenant);
        assertThat(requestedSubject).hasValue(subject);
    }

    @Test
    void returnsEmptyWhenIdentityAssociationIsMissing() {
        TenantId tenant = TenantId.generate();
        var resolver = new EcosystemSocialGraphActorResolver(
                associations((t, s) -> Optional.empty()),
                partyLookup(id -> {
                    throw new AssertionError("Party lookup must not run without an identity association");
                }));

        assertThat(resolver.resolve(tenant, new IdentitySubjectRef("issuer", "unknown"))
                .await().indefinitely()).isEmpty();
    }

    @Test
    void rejectsAssociationReturnedForAnotherTenantOrSubject() {
        TenantId requestedTenant = TenantId.generate();
        TenantId foreignTenant = TenantId.generate();
        PartyId partyId = PartyId.generate();
        IdentitySubjectRef requested = new IdentitySubjectRef("issuer", "requested");
        PartyIdentityAssociation foreign = PartyIdentityAssociation.link(
                UUID.randomUUID(), foreignTenant, partyId,
                new IdentitySubjectRef("issuer", "different"), NOW);
        var resolver = new EcosystemSocialGraphActorResolver(
                associations((t, s) -> Optional.of(foreign)),
                partyLookup(id -> {
                    throw new AssertionError("Party lookup must not run for a mismatched association");
                }));

        assertThat(resolver.resolve(requestedTenant, requested).await().indefinitely()).isEmpty();
    }

    @Test
    void rejectsInactivePartyEvenWhenIdentityAssociationRemainsActive() {
        TenantId tenant = TenantId.generate();
        PartyId partyId = PartyId.generate();
        IdentitySubjectRef subject = new IdentitySubjectRef("issuer", "inactive-party");
        PartyIdentityAssociation association = PartyIdentityAssociation.link(
                UUID.randomUUID(), tenant, partyId, subject, NOW);
        Party inactive = Party.register(partyId, tech.kayys.numou.ecosystem.domain.party.PartyKind.PERSON);
        inactive.deactivate();
        var resolver = new EcosystemSocialGraphActorResolver(
                associations((t, s) -> Optional.of(association)),
                partyLookup(id -> Optional.of(inactive)));

        assertThat(resolver.resolve(tenant, subject).await().indefinitely()).isEmpty();
    }

    private static PartyIdentityAssociationRepository associations(
            AssociationLookup lookup) {
        return new PartyIdentityAssociationRepository() {
            @Override
            public Uni<PartyIdentityAssociation> save(PartyIdentityAssociation association) {
                return Uni.createFrom().item(association);
            }

            @Override
            public Uni<Optional<PartyIdentityAssociation>> findActiveByTenantAndIdentity(
                    TenantId tenantId, IdentitySubjectRef identity) {
                return Uni.createFrom().item(lookup.find(tenantId, identity));
            }

            @Override
            public Uni<List<PartyIdentityAssociation>> findByTenantAndParty(TenantId tenantId, PartyId partyId) {
                return Uni.createFrom().item(List.of());
            }
        };
    }

    private static PartyLookupPort partyLookup(PartyLookup lookup) {
        return partyId -> Uni.createFrom().item(lookup.find(partyId));
    }

    @FunctionalInterface
    private interface AssociationLookup {
        Optional<PartyIdentityAssociation> find(TenantId tenant, IdentitySubjectRef subject);
    }

    @FunctionalInterface
    private interface PartyLookup {
        Optional<Party> find(PartyId partyId);
    }
}
