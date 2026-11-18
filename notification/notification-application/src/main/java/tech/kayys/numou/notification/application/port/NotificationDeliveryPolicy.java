package tech.kayys.numou.notification.application.port;

import tech.kayys.numou.notification.domain.Notification;

/**
 * Delivery-time policy hook. Implementations must evaluate authoritative,
 * current recipient/author policy and must not mutate or delete notification
 * intent. System-originated notifications have no source Party and may be
 * handled according to host policy.
 */
@FunctionalInterface
public interface NotificationDeliveryPolicy {
    Decision evaluate(Notification notification);

    enum Decision {
        ALLOW,
        BLOCKED,
        MUTED,
        UNRESOLVED
    }
}
