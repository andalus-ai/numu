package tech.kayys.numou.support.adapter.security;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.application.port.PartyParticipantAssociationRepository;
import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.party.PartyParticipantAssociation;
import tech.kayys.numou.ecosystem.domain.party.PartyParticipantRole;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.support.domain.ticket.RequesterRole;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DefaultSupportPartyParticipantResolverTest {
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    private static final Instant PAST = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void mapsOnlyCustomerAndPartnerRolesAndPreservesParticipantId() {
        TenantId tenant = TenantId.generate();
        PartyId party = PartyId.generate();
        ParticipantId participant = ParticipantId.generate();

        var customer = resolver(List.of(association(tenant, party, participant, PartyParticipantRole.CUSTOMER, PAST)))
                .resolve(tenant, party).await().indefinitely().orElseThrow();
        assertEquals(participant, customer.participantId());
        assertEquals(RequesterRole.CUSTOMER, customer.requesterRole());

        var partner = resolver(List.of(association(tenant, party, participant, PartyParticipantRole.PARTNER, PAST)))
                .resolve(tenant, party).await().indefinitely().orElseThrow();
        assertEquals(participant, partner.participantId());
        assertEquals(RequesterRole.PARTNER, partner.requesterRole());
    }

    @Test
    void unsupportedOrNotYetEffectiveRolesAreNotResolved() {
        TenantId tenant = TenantId.generate();
        PartyId party = PartyId.generate();
        assertTrue(resolver(List.of(association(tenant, party, ParticipantId.generate(),
                PartyParticipantRole.SUPPLIER, PAST))).resolve(tenant, party).await().indefinitely().isEmpty());

        assertTrue(resolver(List.of(association(tenant, party, ParticipantId.generate(),
                PartyParticipantRole.CUSTOMER, NOW.plusSeconds(3600))))
                .resolve(tenant, party).await().indefinitely().isEmpty());
    }

    @Test
    void ambiguousSupportedRelationshipsFailClosed() {
        TenantId tenant = TenantId.generate();
        PartyId party = PartyId.generate();
        var customer = association(tenant, party, ParticipantId.generate(), PartyParticipantRole.CUSTOMER, PAST);
        var partner = association(tenant, party, ParticipantId.generate(), PartyParticipantRole.PARTNER, PAST);
        assertTrue(resolver(List.of(customer, partner)).resolve(tenant, party).await().indefinitely().isEmpty());
    }

    @Test
    void repositoryLookupIsConstrainedByRequestedTenantAndParty() {
        TenantId tenant = TenantId.generate();
        PartyId party = PartyId.generate();
        AtomicReference<TenantId> requestedTenant = new AtomicReference<>();
        AtomicReference<PartyId> requestedParty = new AtomicReference<>();
        var repository = new StubRepository(List.of()) {
            @Override
            public Uni<List<PartyParticipantAssociation>> findActiveByTenantAndParty(
                    TenantId tenantId, PartyId partyId) {
                requestedTenant.set(tenantId);
                requestedParty.set(partyId);
                return Uni.createFrom().item(List.of());
            }
        };

        new DefaultSupportPartyParticipantResolver(repository, FIXED_CLOCK)
                .resolve(tenant, party).await().indefinitely();
        assertEquals(tenant, requestedTenant.get());
        assertEquals(party, requestedParty.get());
    }

    private static DefaultSupportPartyParticipantResolver resolver(List<PartyParticipantAssociation> rows) {
        return new DefaultSupportPartyParticipantResolver(new StubRepository(rows), FIXED_CLOCK);
    }

    private static PartyParticipantAssociation association(TenantId tenant, PartyId party,
            ParticipantId participant, PartyParticipantRole role, Instant effectiveFrom) {
        return PartyParticipantAssociation.establish(UUID.randomUUID(), tenant, party, participant, role, effectiveFrom);
    }

    private static class StubRepository implements PartyParticipantAssociationRepository {
        private final List<PartyParticipantAssociation> rows;
        StubRepository(List<PartyParticipantAssociation> rows) { this.rows = rows; }

        @Override public Uni<PartyParticipantAssociation> save(PartyParticipantAssociation association) {
            return Uni.createFrom().item(association);
        }
        @Override public Uni<Optional<PartyParticipantAssociation>> findActive(TenantId tenantId, PartyId partyId,
                ParticipantId participantId, PartyParticipantRole role) {
            return Uni.createFrom().item(Optional.empty());
        }
        @Override public Uni<List<PartyParticipantAssociation>> findActiveByTenantAndParty(TenantId tenantId, PartyId partyId) {
            return Uni.createFrom().item(rows.stream()
                    .filter(row -> row.tenantId().equals(tenantId) && row.partyId().equals(partyId)).toList());
        }
        @Override public Uni<List<PartyParticipantAssociation>> findActiveByTenantAndParticipant(
                TenantId tenantId, ParticipantId participantId) {
            return Uni.createFrom().item(List.of());
        }
        @Override public Uni<Optional<PartyParticipantAssociation>> findByTenantAndId(TenantId tenantId, UUID id) {
            return Uni.createFrom().item(Optional.empty());
        }
    }
}
