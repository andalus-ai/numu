package tech.kayys.numou.notification.application.port;

import java.util.Objects;
import java.util.UUID;

/** Provider-neutral boundary; implementations must use bounded network timeouts. */
public interface NotificationDeliveryProvider {
    String providerCode();
    DeliveryResult deliver(DeliveryRequest request);

    record DeliveryRequest(UUID tenantId, UUID notificationId, UUID deviceId, String deviceToken,
                           String title, String body, String sourceReference) {
        public DeliveryRequest {
            Objects.requireNonNull(tenantId, "tenantId is required");
            Objects.requireNonNull(notificationId, "notificationId is required");
            Objects.requireNonNull(deviceId, "deviceId is required");
            Objects.requireNonNull(deviceToken, "deviceToken is required");
            Objects.requireNonNull(title, "title is required");
            Objects.requireNonNull(body, "body is required");
        }
        @Override public String toString() {
            return "DeliveryRequest[tenantId=" + tenantId + ", notificationId=" + notificationId
                    + ", deviceId=" + deviceId + ", deviceToken=[REDACTED], title=[REDACTED], body=[REDACTED]]";
        }
    }

    record DeliveryResult(boolean accepted, boolean retryable, String safeErrorCode) {
        public DeliveryResult {
            safeErrorCode = safeErrorCode == null ? "" : safeErrorCode.trim();
            if (safeErrorCode.length() > 120) throw new IllegalArgumentException("safeErrorCode exceeds 120 characters");
            if (accepted && (retryable || !safeErrorCode.isEmpty()))
                throw new IllegalArgumentException("accepted delivery cannot carry failure metadata");
        }
        public static DeliveryResult success() { return new DeliveryResult(true, false, ""); }
        public static DeliveryResult retryable(String safeErrorCode) { return new DeliveryResult(false, true, safeErrorCode); }
        public static DeliveryResult permanentFailure(String safeErrorCode) { return new DeliveryResult(false, false, safeErrorCode); }
    }
}
