package tech.kayys.numou.crm.domain.repository;

import tech.kayys.numou.crm.domain.identifier.CustomerId;
import tech.kayys.numou.crm.domain.model.CustomerPartyAssociation;
import tech.kayys.numou.ecosystem.domain.party.PartyId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Tenant-scoped persistence contract for explicit Customer-to-Party links.
 *
 * Implementations MUST derive tenant scope from trusted execution context.
 * Neither method accepts a tenant identifier from the caller.
 */
public interface CustomerPartyAssociationRepository {

    CompletionStage<Optional<CustomerPartyAssociation>> findByCustomerId(CustomerId customerId);

    CompletionStage<Optional<CustomerPartyAssociation>> findByPartyId(PartyId partyId);

    CompletionStage<CustomerPartyAssociation> save(CustomerPartyAssociation association);
}
