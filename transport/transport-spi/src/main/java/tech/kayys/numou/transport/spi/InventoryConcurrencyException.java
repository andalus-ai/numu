package tech.kayys.numou.transport.spi;

/** Signals that an inventory write lost a compare-and-set race or targeted a missing snapshot. */
public final class InventoryConcurrencyException extends RuntimeException {
    public InventoryConcurrencyException(String message) {
        super(message);
    }

    public InventoryConcurrencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
