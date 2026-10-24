package tech.kayys.numou.omnichannel.core;

/**
 * Durable inbox lifecycle for inbound provider events.
 *
 * <p>Persistence adapters must apply transitions atomically (compare-and-set
 * or equivalent) and persist attempt counts and failure details alongside the
 * state. This enum is a contract, not a persistence implementation.</p>
 */
public enum InboundDeliveryState {
    RECEIVED,
    VERIFIED,
    REJECTED,
    PROCESSING,
    PROCESSED,
    RETRYABLE_FAILURE,
    DEAD_LETTER;

    public boolean canTransitionTo(InboundDeliveryState next) {
        if (next == null || next == this) {
            return false;
        }
        return switch (this) {
            case RECEIVED -> next == VERIFIED || next == REJECTED;
            case VERIFIED -> next == PROCESSING || next == REJECTED;
            case PROCESSING -> next == PROCESSED || next == RETRYABLE_FAILURE || next == DEAD_LETTER;
            case RETRYABLE_FAILURE -> next == PROCESSING || next == DEAD_LETTER;
            case REJECTED, PROCESSED, DEAD_LETTER -> false;
        };
    }

    public boolean isTerminal() {
        return this == REJECTED || this == PROCESSED || this == DEAD_LETTER;
    }
}
