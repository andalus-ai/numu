package tech.kayys.numou.attendee.application.error;

/** Expected conflict when a tenant already has the Party/profile-kind pair. */
public final class AttendeeProfileAlreadyExistsException extends RuntimeException {
    public AttendeeProfileAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
