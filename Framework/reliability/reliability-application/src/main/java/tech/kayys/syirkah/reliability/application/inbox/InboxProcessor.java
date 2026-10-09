package tech.kayys.syirkah.reliability.application.inbox;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.reliability.spi.port.InboxStorePort;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Deduplicates inbound events before the domain sees them.
 *
 * <p>Semantics: an event is handled at-least-once and the handler must be idempotent.
 * If the handler failed (or the process died) the event is <em>not</em> marked handled,
 * so the broker's redelivery runs it again. Only an event already marked handled is skipped.
 */
public final class InboxProcessor {

    private final InboxStorePort inbox;

    public InboxProcessor(InboxStorePort inbox) {
        this.inbox = Objects.requireNonNull(inbox, "inbox cannot be null");
    }

    /** @return true if the handler ran, false if the event was an already-handled duplicate */
    public Uni<Boolean> process(String eventId, String eventType, Supplier<Uni<Void>> handler) {
        return inbox.recordArrival(eventId, eventType).flatMap(arrival -> {
            if (arrival.entry().alreadyProcessed()) {
                return Uni.createFrom().item(false);
            }
            return Uni.createFrom().<Void>deferred(() -> handler.get())
                    .flatMap(ignored -> inbox.markHandled(arrival.entry()))
                    .replaceWith(true);
        });
    }
}
