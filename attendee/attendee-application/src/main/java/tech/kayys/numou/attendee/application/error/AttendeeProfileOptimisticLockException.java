package tech.kayys.numou.attendee.application.error;

/** The profile revision no longer matches the persisted aggregate revision. */
public final class AttendeeProfileOptimisticLockException extends RuntimeException {
    public AttendeeProfileOptimisticLockException(String message) {
        super(message);
    }
}
