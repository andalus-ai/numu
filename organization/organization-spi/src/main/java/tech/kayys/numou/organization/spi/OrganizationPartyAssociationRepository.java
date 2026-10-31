package tech.kayys.numou.organization.spi;

import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationPartyAssociation;
import tech.kayys.numou.organization.domain.OrganizationPartyAssociationHistory;
import tech.kayys.numou.ecosystem.domain.party.PartyId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Persistence contract owned by the Organization capability. */
public interface OrganizationPartyAssociationRepository {
    CompletionStage<OrganizationPartyAssociation> save(OrganizationPartyAssociation association);
    CompletionStage<Optional<OrganizationPartyAssociation>> findById(UUID associationId);
    CompletionStage<Optional<OrganizationPartyAssociation>> findCurrentByOrganizationId(OrganizationId organizationId);
    CompletionStage<Optional<OrganizationPartyAssociation>> findCurrentByPartyId(PartyId partyId);
    CompletionStage<List<OrganizationPartyAssociationHistory>> findHistory(UUID associationId);
}
