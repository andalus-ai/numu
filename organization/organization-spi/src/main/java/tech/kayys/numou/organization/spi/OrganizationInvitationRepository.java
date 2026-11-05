package tech.kayys.numou.organization.spi;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationInvitation;
import tech.kayys.numou.organization.domain.OrganizationInvitationId;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface OrganizationInvitationRepository {
    CompletionStage<OrganizationInvitation> save(OrganizationInvitation invitation, TenantId tenantId);
    CompletionStage<Optional<OrganizationInvitation>> findByTenantAndId(
            TenantId tenantId, OrganizationInvitationId invitationId);
    CompletionStage<Optional<OrganizationInvitation>> findByTenantAndTokenHash(
            TenantId tenantId, String tokenHash);
    CompletionStage<Optional<OrganizationInvitation>> findPendingByTenantAndOrganizationAndEmail(
            TenantId tenantId, OrganizationId organizationId, String normalizedEmail);
}