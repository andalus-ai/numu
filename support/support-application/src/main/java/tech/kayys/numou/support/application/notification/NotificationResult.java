package tech.kayys.numou.support.application.notification;

public record NotificationResult(String notificationId, Status status) {

    public NotificationResult {
        if (notificationId == null || notificationId.isBlank()) {
            throw new IllegalArgumentException("notificationId cannot be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
    }

    public enum Status {
        ACCEPTED,
        SENT,
        FAILED
    }
}
