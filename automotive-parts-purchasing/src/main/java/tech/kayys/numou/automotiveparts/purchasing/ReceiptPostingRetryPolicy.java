package tech.kayys.numou.automotiveparts.purchasing;

/** Small, deployment-configurable retry budget for durable goods-receipt posting. */
public record ReceiptPostingRetryPolicy(int maxAttempts) {
    public ReceiptPostingRetryPolicy {
        if (maxAttempts < 1 || maxAttempts > 100) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and 100");
        }
    }

    public static ReceiptPostingRetryPolicy defaults() {
        return new ReceiptPostingRetryPolicy(ReceiptPostingLedger.DEFAULT_MAX_ATTEMPTS);
    }
}
