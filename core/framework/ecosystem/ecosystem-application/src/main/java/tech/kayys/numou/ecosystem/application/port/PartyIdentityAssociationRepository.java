package tech.kayys.numou.ecosystem.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.party.PartyIdentityAssociation;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.List;
import java.util.Optional;

/**
 * Persistence boundary for explicit Party-to-authenticated-subject associations.
 * Adapters must enforce tenant-scoped uniqueness for active identity subjects.
 */
public interface PartyIdentityAssociationRepository {

    Uni<PartyIdentityAssociation> save(PartyIdentityAssociation association);

    Uni<Optional<PartyIdentityAssociation>> findActiveByTenantAndIdentity(
            TenantId tenantId,
            IdentitySubjectRef identity);

    Uni<List<PartyIdentityAssociation>> findByTenantAndParty(
            TenantId tenantId,
            PartyId partyId);
}
