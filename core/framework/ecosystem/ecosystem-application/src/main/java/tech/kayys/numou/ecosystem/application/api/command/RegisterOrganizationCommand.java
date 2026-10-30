package tech.kayys.numou.ecosystem.application.api.command;

import tech.kayys.numou.ecosystem.domain.organization.OrganizationType;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;
import java.util.UUID;

/** Creates an organization profile for an existing canonical Party. */
public record RegisterOrganizationCommand(
        PartyId partyId,
        String legalName,
        String displayName,
        OrganizationType type,
        UUID tenantId) implements Command {

    public RegisterOrganizationCommand {
        Objects.requireNonNull(partyId, "partyId cannot be null");
        Objects.requireNonNull(legalName, "legalName cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
    }
}
