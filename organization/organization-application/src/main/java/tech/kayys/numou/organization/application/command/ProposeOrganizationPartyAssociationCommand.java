package tech.kayys.numou.organization.application.command;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.organization.domain.OrganizationId;
import java.time.Instant;
import java.util.UUID;
/** Caller supplies a stable associationId so retries address the same proposal. */
public record ProposeOrganizationPartyAssociationCommand(UUID associationId, TenantId tenantId,
 OrganizationId organizationId, PartyId partyId, Instant proposedAt, String actorId, String evidenceReference) {}
