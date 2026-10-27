package tech.kayys.numou.events.spi;

/** Stable application-facing error classification; HTTP adapters may map codes to transport responses. */
public final class TicketBookingException extends RuntimeException {
    public enum Code {
        INVENTORY_NOT_FOUND, INSUFFICIENT_INVENTORY, IDEMPOTENCY_CONFLICT,
        BOOKING_NOT_FOUND, INVALID_BOOKING_STATE, INVALID_PAYMENT_REFERENCE, PERSISTENCE_FAILURE
    }

    private final Code code;

    public TicketBookingException(Code code, String message) {
        super(message);
        this.code = java.util.Objects.requireNonNull(code, "code");
    }

    public TicketBookingException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = java.util.Objects.requireNonNull(code, "code");
    }

    public Code code() { return code; }
}
