package tech.kayys.numou.affiliate.application;

/**
 * Indicates that one or more claimed events could not be safely completed or
 * rescheduled after the dispatcher finished attempting the entire claimed batch.
 */
public final class AffiliateOutboxDispatchException extends RuntimeException {
    private final int deliveredCount;

    public AffiliateOutboxDispatchException(int deliveredCount, Throwable cause) {
        super("Affiliate outbox batch completed with an event-level persistence failure", cause);
        if (deliveredCount < 0) throw new IllegalArgumentException("deliveredCount cannot be negative");
        this.deliveredCount = deliveredCount;
    }

    /** Number of events acknowledged successfully before the batch-level failure. */
    public int deliveredCount() {
        return deliveredCount;
    }
}
