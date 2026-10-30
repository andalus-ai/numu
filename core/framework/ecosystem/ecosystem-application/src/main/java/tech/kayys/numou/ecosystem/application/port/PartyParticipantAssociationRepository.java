package tech.kayys.numou.ecosystem.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.party.PartyParticipantAssociation;
import tech.kayys.numou.ecosystem.domain.party.PartyParticipantRole;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped persistence port for Party-to-Participant business relationships. */
public interface PartyParticipantAssociationRepository {
    Uni<PartyParticipantAssociation> save(PartyParticipantAssociation association);
    Uni<Optional<PartyParticipantAssociation>> findActive(
            TenantId tenantId, PartyId partyId, ParticipantId participantId, PartyParticipantRole role);
    Uni<List<PartyParticipantAssociation>> findActiveByTenantAndParty(TenantId tenantId, PartyId partyId);
    Uni<List<PartyParticipantAssociation>> findActiveByTenantAndParticipant(TenantId tenantId, ParticipantId participantId);
    Uni<Optional<PartyParticipantAssociation>> findByTenantAndId(TenantId tenantId, UUID id);
}
