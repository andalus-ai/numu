package tech.kayys.numou.event.spi.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;

import java.util.List;

/**
 * Durable append-only event storage (base01.md §3 event-store).
 *
 * <p>Replay is a first-class capability, not an afterthought: rebuilding a
 * read model or onboarding a late-joining participant both need it.
 */
public interface EventStorePort {

    /** Appends envelopes durably. Never overwrites. */
    Uni<Void> append(List<EventEnvelope<? extends BusinessEvent>> envelopes);

    /** Loads the events of one aggregate, in append order. */
    Uni<List<EventEnvelope<? extends BusinessEvent>>> loadByAggregate(
            String aggregateType,
            String aggregateId);

    /**
     * Loads events after a global position - the projection / replay cursor.
     */
    Uni<List<EventEnvelope<? extends BusinessEvent>>> loadAfter(long globalPosition, int limit);
}
