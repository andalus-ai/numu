package tech.kayys.numou.socialgraph.persistence.jdbc;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.application.port.PartyIdentityAssociationRepository;
import tech.kayys.numou.ecosystem.application.port.PartyLookupPort;
import tech.kayys.numou.ecosystem.domain.model.Party;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.party.PartyIdentityAssociation;
import tech.kayys.numou.ecosystem.domain.party.PartyKind;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EcosystemSocialGraphIdentityAdapterTest {
    @Test
    void resolvesAuthenticatedSubjectOnlyThroughExplicitTenantScopedPartyAssociation() {
        TenantId tenant = TenantId.generate();
        Party party = Party.register(PartyId.generate(), PartyKind.PERSON);
        IdentitySubjectRef subject = new IdentitySubjectRef("https://issuer.example", "subject-123");
        PartyIdentityAssociation association = PartyIdentityAssociation.link(
                UUID.randomUUID(), tenant, party.getId(), subject, Instant.parse("2026-11-04T00:00:00Z"));

        PartyLookupPort lookup = id -> Uni.createFrom().item(
                id.equals(party.getId()) ? Optional.of(party) : Optional.empty());
        PartyIdentityAssociationRepository associations = new PartyIdentityAssociationRepository() {
            @Override public Uni<PartyIdentityAssociation> save(PartyIdentityAssociation value) {
                return Uni.createFrom().item(value);
            }
            @Override public Uni<Optional<PartyIdentityAssociation>> findActiveByTenantAndIdentity(
                    TenantId requestedTenant, IdentitySubjectRef requestedSubject) {
                return Uni.createFrom().item(tenant.equals(requestedTenant) && subject.equals(requestedSubject)
                        ? Optional.of(association) : Optional.empty());
            }
            @Override public Uni<List<PartyIdentityAssociation>> findByTenantAndParty(TenantId requestedTenant, PartyId partyId) {
                return Uni.createFrom().item(List.of());
            }
        };

        EcosystemSocialGraphIdentityAdapter adapter = new EcosystemSocialGraphIdentityAdapter(lookup, associations);

        assertThat(adapter.resolve(tenant, subject).await().indefinitely()).hasValueSatisfying(actor -> {
            assertThat(actor.tenantId()).isEqualTo(tenant);
            assertThat(actor.partyId()).isEqualTo(party.getId());
        });
        assertThat(adapter.resolve(TenantId.generate(), subject).await().indefinitely()).isEmpty();
        assertThat(adapter.isActive(party.getId()).await().indefinitely()).isTrue();
    }
}
