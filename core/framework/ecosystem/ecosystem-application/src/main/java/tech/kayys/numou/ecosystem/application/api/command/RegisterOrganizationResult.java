package tech.kayys.numou.ecosystem.application.api.command;

import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.organization.OrganizationStatus;

/** Result of registering an organization profile. */
public record RegisterOrganizationResult(PartyId partyId, OrganizationStatus status) {
}
