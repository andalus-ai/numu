package tech.kayys.numou.pos.application;

import tech.kayys.numou.pos.application.port.in.PosEventInbox;
import tech.kayys.numou.pos.application.port.out.PosOutboxEvent;

import java.util.Objects;

/** Shared entry point for idempotent handling of POS integration events. */
public final class PosEventConsumer {
    private final PosEventInbox inbox;

    public PosEventConsumer(PosEventInbox inbox) {
        this.inbox = Objects.requireNonNull(inbox, "inbox cannot be null");
    }

    /**
     * Consumer names identify a logical subscription, not a process instance.
     * Use a stable name per business effect so independent consumers each run once.
     */
    public boolean consume(String consumerName, PosOutboxEvent event, Runnable handler) {
        Objects.requireNonNull(event, "event cannot be null");
        Objects.requireNonNull(handler, "handler cannot be null");
        if (consumerName == null || consumerName.isBlank()) {
            throw new IllegalArgumentException("consumerName cannot be blank");
        }
        return inbox.executeOnce(event.tenantId(), consumerName.trim(), event.eventId(), handler);
    }
}
