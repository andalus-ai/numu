package tech.kayys.numou.attendee.application.port;

/** Raised when a tenant already has the requested attendee profile identity. */
public final class AttendeeProfileAlreadyExistsException extends RuntimeException {
    public AttendeeProfileAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
