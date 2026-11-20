package tech.kayys.numou.talent.application.port;

/** Raised when an idempotency key is reused for a different command fingerprint. */
public final class TalentProfileCommandIdempotencyConflictException extends RuntimeException {
    public TalentProfileCommandIdempotencyConflictException() {
        super("Talent command idempotency key was already used for a different command");
    }
}
