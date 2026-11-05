package tech.kayys.numou.socialgraph.application;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.application.port.PartyIdentityAssociationRepository;
import tech.kayys.numou.ecosystem.application.port.PartyLookupPort;
import tech.kayys.numou.ecosystem.domain.model.Party;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.party.PartyIdentityAssociation;
import tech.kayys.numou.ecosystem.domain.party.PartyKind;
import tech.kayys.numou.ecosystem.domain.party.PartyStatus;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EcosystemSocialGraphIdentityAdapterTest {
    private final TenantId tenant = TenantId.generate();
    private final PartyId partyId = PartyId.generate();
    private final IdentitySubjectRef subject = new IdentitySubjectRef(
            "https://identity.example", "subject-42");

    @Test
    void resolvesOnlyTenantScopedActiveIdentityAssociationToActiveCanonicalParty() {
        PartyIdentityAssociation association = PartyIdentityAssociation.link(
                UUID.randomUUID(), tenant, partyId, subject, Instant.parse("2026-11-01T00:00:00Z"));
        PartyLookupPort lookup = id -> Uni.createFrom().item(
                Optional.of(Party.rehydrate(id, PartyKind.PERSON, PartyStatus.ACTIVE)));
        EcosystemSocialGraphActorResolver resolver = new EcosystemSocialGraphActorResolver(
                associations(Optional.of(association)), lookup);

        Optional<SocialGraphActor> result = resolver.resolve(tenant, subject).await().indefinitely();

        assertThat(result).contains(new SocialGraphActor(tenant, partyId));
    }

    @Test
    void missingIdentityAssociationDoesNotResolveAnActor() {
        EcosystemSocialGraphActorResolver resolver = new EcosystemSocialGraphActorResolver(
                associations(Optional.empty()),
                id -> Uni.createFrom().item(Optional.empty()));

        assertThat(resolver.resolve(tenant, subject).await().indefinitely()).isEmpty();
    }

    @Test
    void inactiveCanonicalPartyDoesNotResolveAnActor() {
        PartyIdentityAssociation association = PartyIdentityAssociation.link(
                UUID.randomUUID(), tenant, partyId, subject, Instant.parse("2026-11-01T00:00:00Z"));
        EcosystemSocialGraphActorResolver resolver = new EcosystemSocialGraphActorResolver(
                associations(Optional.of(association)),
                id -> Uni.createFrom().item(Optional.of(
                        Party.rehydrate(id, PartyKind.PERSON, PartyStatus.INACTIVE))));

        assertThat(resolver.resolve(tenant, subject).await().indefinitely()).isEmpty();
    }

    @Test
    void rejectsAssociationReturnedForDifferentTenantOrSubject() {
        PartyIdentityAssociation wrongTenant = PartyIdentityAssociation.link(
                UUID.randomUUID(), TenantId.generate(), partyId, subject, Instant.parse("2026-11-01T00:00:00Z"));
        EcosystemSocialGraphActorResolver tenantResolver = new EcosystemSocialGraphActorResolver(
                associations(Optional.of(wrongTenant)),
                id -> Uni.createFrom().item(Optional.of(
                        Party.rehydrate(id, PartyKind.PERSON, PartyStatus.ACTIVE))));

        assertThat(tenantResolver.resolve(tenant, subject).await().indefinitely()).isEmpty();

        IdentitySubjectRef otherSubject = new IdentitySubjectRef(
                "https://identity.example", "different-subject");
        PartyIdentityAssociation wrongSubject = PartyIdentityAssociation.link(
                UUID.randomUUID(), tenant, partyId, otherSubject, Instant.parse("2026-11-01T00:00:00Z"));
        EcosystemSocialGraphActorResolver subjectResolver = new EcosystemSocialGraphActorResolver(
                associations(Optional.of(wrongSubject)),
                id -> Uni.createFrom().item(Optional.of(
                        Party.rehydrate(id, PartyKind.PERSON, PartyStatus.ACTIVE))));

        assertThat(subjectResolver.resolve(tenant, subject).await().indefinitely()).isEmpty();
    }

    @Test
    void partyDirectoryAdapterUsesCanonicalPartyLifecycle() {
        EcosystemSocialGraphPartyDirectoryAdapter active = new EcosystemSocialGraphPartyDirectoryAdapter(
                id -> Uni.createFrom().item(Optional.of(
                        Party.rehydrate(id, PartyKind.PERSON, PartyStatus.ACTIVE))));
        EcosystemSocialGraphPartyDirectoryAdapter inactive = new EcosystemSocialGraphPartyDirectoryAdapter(
                id -> Uni.createFrom().item(Optional.of(
                        Party.rehydrate(id, PartyKind.PERSON, PartyStatus.INACTIVE))));
        EcosystemSocialGraphPartyDirectoryAdapter missing = new EcosystemSocialGraphPartyDirectoryAdapter(
                id -> Uni.createFrom().item(Optional.empty()));

        assertThat(active.isActive(partyId).await().indefinitely()).isTrue();
        assertThat(inactive.isActive(partyId).await().indefinitely()).isFalse();
        assertThat(missing.isActive(partyId).await().indefinitely()).isFalse();
    }

    private PartyIdentityAssociationRepository associations(
            Optional<PartyIdentityAssociation> result) {
        return new PartyIdentityAssociationRepository() {
            @Override
            public Uni<PartyIdentityAssociation> save(PartyIdentityAssociation association) {
                return Uni.createFrom().item(association);
            }

            @Override
            public Uni<Optional<PartyIdentityAssociation>> findActiveByTenantAndIdentity(
                    TenantId requestedTenant, IdentitySubjectRef requestedSubject) {
                return Uni.createFrom().item(result);
            }

            @Override
            public Uni<List<PartyIdentityAssociation>> findByTenantAndParty(
                    TenantId requestedTenant, PartyId requestedParty) {
                return Uni.createFrom().item(List.of());
            }
        };
    }
}
