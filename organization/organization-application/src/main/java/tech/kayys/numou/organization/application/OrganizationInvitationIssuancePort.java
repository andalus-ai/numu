package tech.kayys.numou.organization.application;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.organization.domain.OrganizationInvitation;
import java.util.concurrent.CompletionStage;

/** Atomic write boundary for the invitation aggregate and its encrypted delivery intent. */
@FunctionalInterface
public interface OrganizationInvitationIssuancePort {
    CompletionStage<Void> persist(OrganizationInvitation invitation,
            OrganizationInvitationDeliveryOutboxPort.DeliveryMessage delivery, TenantId tenantId);
}
