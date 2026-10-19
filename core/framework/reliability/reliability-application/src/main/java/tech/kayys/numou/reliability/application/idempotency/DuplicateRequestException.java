package tech.kayys.numou.reliability.application.idempotency;

/** The same idempotency key is currently being processed (or produced no replayable result). Map to HTTP 409. */
public final class DuplicateRequestException extends RuntimeException {

    public DuplicateRequestException(String key) {
        super("Request with idempotency key is already being processed: " + key);
    }
}
