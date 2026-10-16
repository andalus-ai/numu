package tech.kayys.syirkah.accounting.application.outbox;

import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.UUID;

/**
 * Port for persisting and querying outbox events.
 */
public interface OutboxRepository {
    Uni<Void> save(OutboxEvent event);
    Uni<List<OutboxEvent>> findUnprocessed(int limit);
    Uni<Void> markProcessed(UUID id);
}
