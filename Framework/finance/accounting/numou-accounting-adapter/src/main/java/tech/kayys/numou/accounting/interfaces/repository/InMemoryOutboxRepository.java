package tech.kayys.syirkah.accounting.interfaces.repository;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.syirkah.accounting.application.outbox.OutboxEvent;
import tech.kayys.syirkah.accounting.application.outbox.OutboxRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class InMemoryOutboxRepository implements OutboxRepository {
    private final Map<UUID, OutboxEvent> store = new ConcurrentHashMap<>();

    @Override
    public Uni<Void> save(OutboxEvent event) {
        store.put(event.id(), event);
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<List<OutboxEvent>> findUnprocessed(int limit) {
        List<OutboxEvent> unprocessed = store.values().stream()
                .filter(e -> !e.isProcessed())
                .limit(limit)
                .toList();
        return Uni.createFrom().item(unprocessed);
    }

    @Override
    public Uni<Void> markProcessed(UUID id) {
        OutboxEvent event = store.get(id);
        if (event != null) {
            store.put(id, event.markProcessed());
        }
        return Uni.createFrom().voidItem();
    }
}
