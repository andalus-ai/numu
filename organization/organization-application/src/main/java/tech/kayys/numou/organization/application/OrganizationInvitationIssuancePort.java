package tech.kayys.numou.organization.application;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.organization.domain.OrganizationInvitation;
import java.time.Instant;
import java.util.concurrent.CompletionStage;

/** Atomic write boundary for invitation state and its encrypted delivery intent. */
public interface OrganizationInvitationIssuancePort {
    CompletionStage<Void> persist(OrganizationInvitation invitation,
            OrganizationInvitationDeliveryOutboxPort.DeliveryMessage delivery, TenantId tenantId);

    /** Rotates the bearer digest, supersedes older queued sends, and appends a new delivery atomically. */
    CompletionStage<Void> rotateAndPersist(OrganizationInvitation invitation,
            OrganizationInvitationDeliveryOutboxPort.DeliveryMessage delivery, TenantId tenantId,
            int expectedVersion, Instant changedAt);
}
