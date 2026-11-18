package tech.kayys.numou.automotiveparts.purchasing;

/** A tenant-scoped receipt posting is absent or is no longer in the dead-letter state. */
public final class ReceiptPostingNotFoundException extends RuntimeException {
    public ReceiptPostingNotFoundException() {
        super("Dead-letter receipt posting not found");
    }
}
