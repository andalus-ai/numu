package tech.kayys.numou.organization.application;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.organization.domain.OrganizationInvitationId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Durable queue contract. All state transitions are fenced by tenant and lease token. */
public interface OrganizationInvitationDeliveryOutboxPort {
    CompletionStage<Void> enqueue(DeliveryMessage message);
    CompletionStage<List<ClaimedDelivery>> claim(Instant now, Instant leaseUntil, int limit);
    CompletionStage<Boolean> markSent(UUID id, TenantId tenant, UUID leaseToken, Instant sentAt);
    CompletionStage<Boolean> retry(UUID id, TenantId tenant, UUID leaseToken, Instant availableAt, String errorCode);
    CompletionStage<Boolean> deadLetter(UUID id, TenantId tenant, UUID leaseToken, Instant failedAt, String errorCode);

    record DeliveryMessage(UUID deliveryId, TenantId tenantId, OrganizationInvitationId invitationId,
                           String recipientEmail, InvitationSecretCipherPort.EncryptedSecret secret, Instant createdAt) {
        public DeliveryMessage {
            if (deliveryId == null || tenantId == null || invitationId == null || recipientEmail == null
                    || recipientEmail.isBlank() || secret == null || createdAt == null)
                throw new IllegalArgumentException("Complete delivery message is required");
        }
    }

    record ClaimedDelivery(UUID deliveryId, TenantId tenantId, OrganizationInvitationId invitationId,
                           String recipientEmail, InvitationSecretCipherPort.EncryptedSecret secret,
                           int attempts, UUID leaseToken, Instant leaseUntil) { }
}
