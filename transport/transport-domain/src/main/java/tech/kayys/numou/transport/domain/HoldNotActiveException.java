package tech.kayys.numou.transport.domain;

/** A business-state conflict: the hold is no longer active and cannot be confirmed. */
public final class HoldNotActiveException extends IllegalStateException {
    public HoldNotActiveException(String message) {
        super(message);
    }
}
