package tech.kayys.numou.reliability.inbox;

import java.util.Objects;

/**
 * Result of recording an event's arrival in the inbox.
 *
 * @param entry     the inbox row for this event id
 * @param firstTime true when this call was the first arrival - the caller
 *                  may process; false means safely ignore the duplicate
 */
public record InboxArrival(InboxEntry entry, boolean firstTime) {

    public InboxArrival {
        Objects.requireNonNull(entry, "entry cannot be null");
    }

    public boolean isDuplicate() {
        return !firstTime;
    }
}
