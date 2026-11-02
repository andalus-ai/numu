package tech.kayys.numou.organization.application.command;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.util.UUID;
public record ActivateOrganizationPartyAssociationCommand(UUID associationId,TenantId tenantId,
 Instant activatedAt,String actorId) {}
