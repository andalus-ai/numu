package tech.kayys.numou.crm.application.api.command;

import tech.kayys.numou.crm.domain.identifier.CustomerId;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;

/** Explicitly links an existing tenant-scoped Customer to an existing Party. */
public record AssociateCustomerWithPartyCommand(
        CustomerId customerId,
        PartyId partyId) implements Command {

    public AssociateCustomerWithPartyCommand {
        Objects.requireNonNull(customerId, "customerId cannot be null");
        Objects.requireNonNull(partyId, "partyId cannot be null");
    }
}
