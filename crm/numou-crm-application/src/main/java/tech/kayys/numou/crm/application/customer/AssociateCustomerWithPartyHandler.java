package tech.kayys.numou.crm.application.customer;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.api.command.AssociateCustomerWithPartyCommand;
import tech.kayys.numou.crm.domain.model.CustomerPartyAssociation;
import tech.kayys.numou.crm.domain.repository.CustomerPartyAssociationRepository;
import tech.kayys.numou.crm.domain.repository.CustomerRepository;
import tech.kayys.numou.ecosystem.application.port.PartyLookupPort;
import tech.kayys.numou.ecosystem.domain.model.Party;

import java.util.Objects;

/**
 * Validates both sides of an explicit association before persisting it.
 * CustomerRepository and CustomerPartyAssociationRepository are tenant-scoped
 * by their adapters; Party itself remains a global canonical identity.
 */
public final class AssociateCustomerWithPartyHandler {

    private final CustomerRepository customerRepository;
    private final PartyLookupPort partyLookupPort;
    private final CustomerPartyAssociationRepository associationRepository;

    public AssociateCustomerWithPartyHandler(
            CustomerRepository customerRepository,
            PartyLookupPort partyLookupPort,
            CustomerPartyAssociationRepository associationRepository) {
        this.customerRepository = Objects.requireNonNull(customerRepository, "customerRepository cannot be null");
        this.partyLookupPort = Objects.requireNonNull(partyLookupPort, "partyLookupPort cannot be null");
        this.associationRepository = Objects.requireNonNull(associationRepository, "associationRepository cannot be null");
    }

    public Uni<CustomerPartyAssociation> handle(AssociateCustomerWithPartyCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        return Uni.createFrom().completionStage(customerRepository.findById(command.customerId()))
                .onItem().transformToUni(customer -> {
                    if (customer.isEmpty()) {
                        return Uni.createFrom().failure(new IllegalArgumentException("Customer not found"));
                    }
                    return partyLookupPort.findParty(command.partyId())
                            .onItem().transformToUni(party -> {
                                if (party.isEmpty()) {
                                    return Uni.createFrom().failure(new IllegalArgumentException("Party not found"));
                                }
                                Party canonicalParty = party.get();
                                if (!canonicalParty.isActive()) {
                                    return Uni.createFrom().failure(new IllegalArgumentException("Party is inactive"));
                                }
                                return Uni.createFrom().completionStage(
                                                associationRepository.findByCustomerId(command.customerId()))
                                        .onItem().transformToUni(existing -> {
                                            if (existing.isPresent()) {
                                                if (existing.get().partyId().equals(command.partyId())) {
                                                    return Uni.createFrom().item(existing.get());
                                                }
                                                return Uni.createFrom().failure(new IllegalStateException(
                                                        "Customer is already associated with a different Party"));
                                            }
                                            CustomerPartyAssociation association =
                                                    CustomerPartyAssociation.associate(command.customerId(), command.partyId());
                                            return Uni.createFrom().completionStage(
                                                    associationRepository.save(association));
                                        });
                            });
                });
    }
}
