package tech.kayys.numou.reliability.inbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A received event recorded before the consumer's domain ever sees it
 * (base01.md §P1-15).
 *
 * <p>The example from the plan, made concrete: event
 * {@code EVT-123 / DeliveryCompleted} arrives. If it has been seen before,
 * the consumer safely ignores it; otherwise it processes it once and marks
 * it handled.
 *
 * @param entryId     unique row identity
 * @param eventId     the arriving event's id - the deduplication key
 * @param eventType   dotted event type
 * @param receivedAt  arrival time
 * @param handled     whether the domain has processed it
 */
public record InboxEntry(
        UUID entryId,
        String eventId,
        String eventType,
        Instant receivedAt,
        boolean handled) {

    public InboxEntry {
        Objects.requireNonNull(entryId, "entryId cannot be null");
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(receivedAt, "receivedAt cannot be null");
        if (eventId.isBlank()) {
            throw new IllegalArgumentException("eventId cannot be blank");
        }
    }

    /** Records the first arrival of an event. */
    public static InboxEntry firstArrival(String eventId, String eventType) {
        return new InboxEntry(UUID.randomUUID(), eventId, eventType, Instant.now(), false);
    }

    /** Returns a copy marked as processed by the domain. */
    public InboxEntry markHandled() {
        return new InboxEntry(entryId, eventId, eventType, receivedAt, true);
    }

    public boolean alreadyProcessed() {
        return handled;
    }
}
