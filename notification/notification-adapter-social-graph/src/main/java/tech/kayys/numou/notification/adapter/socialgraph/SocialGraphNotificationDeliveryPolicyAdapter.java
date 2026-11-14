package tech.kayys.numou.notification.adapter.socialgraph;

import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.notification.application.port.NotificationDeliveryPolicy;
import tech.kayys.numou.notification.domain.Notification;
import tech.kayys.numou.socialgraph.application.SocialGraphInteractionDecision;
import tech.kayys.numou.socialgraph.application.SocialGraphInteractionKind;
import tech.kayys.numou.socialgraph.application.SocialGraphInteractionPolicyService;

import java.util.Objects;

/**
 * Bridges Notification delivery to the authoritative Social Graph policy.
 * The recipient is the viewer/actor and the source Party is the author/target:
 * BLOCK is symmetric; MUTE remains recipient -> author.
 */
public final class SocialGraphNotificationDeliveryPolicyAdapter implements NotificationDeliveryPolicy {
    private final SocialGraphInteractionPolicyService policies;
    private final NotificationRecipientPartyResolver recipients;

    public SocialGraphNotificationDeliveryPolicyAdapter(
            SocialGraphInteractionPolicyService policies,
            NotificationRecipientPartyResolver recipients) {
        this.policies = Objects.requireNonNull(policies, "policies is required");
        this.recipients = Objects.requireNonNull(recipients, "recipients is required");
    }

    @Override
    public Decision evaluate(Notification notification) {
        Objects.requireNonNull(notification, "notification is required");
        if (notification.sourceActorPartyId() == null) {
            // System-originated notifications have no social author to block or mute.
            return Decision.ALLOW;
        }
        TenantId tenantId = TenantId.of(notification.tenantId());
        PartyId recipient = recipients.resolveRecipient(tenantId, notification.recipientId()).orElse(null);
        PartyId author = recipients.resolveSourceActor(tenantId, notification.sourceActorPartyId()).orElse(null);
        if (recipient == null || author == null) {
            // Both endpoints must resolve as active Parties in this tenant.
            // Never fall back to comparing arbitrary UUIDs.
            return Decision.UNRESOLVED;
        }
        SocialGraphInteractionDecision decision = policies.evaluate(
                tenantId, recipient, author, SocialGraphInteractionKind.NOTIFICATION_DELIVERY)
                .await().indefinitely();
        return switch (decision) {
            case ALLOWED -> Decision.ALLOW;
            case BLOCKED -> Decision.BLOCKED;
            case MUTED -> Decision.MUTED;
        };
    }
}
