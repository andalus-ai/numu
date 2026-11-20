package tech.kayys.numou.attendee.application.port;

/** Raised when a profile revision is stale or skips an expected revision. */
public final class AttendeeProfileOptimisticLockException extends RuntimeException {
    public AttendeeProfileOptimisticLockException(String message) {
        super(message);
    }
}
