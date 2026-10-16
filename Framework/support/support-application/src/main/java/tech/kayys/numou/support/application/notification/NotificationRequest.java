package tech.kayys.numou.support.application.notification;

import java.util.Map;
import java.util.Objects;

public record NotificationRequest(
        NotificationChannel channel,
        String recipient,
        String templateCode,
        Map<String, String> parameters,
        NotificationPriority priority,
        String idempotencyKey
) {
    public NotificationRequest {
        Objects.requireNonNull(channel, "channel cannot be null");
        requireText(recipient, "recipient");
        requireText(templateCode, "templateCode");
        requireText(idempotencyKey, "idempotencyKey");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        priority = priority == null ? NotificationPriority.NORMAL : priority;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
    }
}
