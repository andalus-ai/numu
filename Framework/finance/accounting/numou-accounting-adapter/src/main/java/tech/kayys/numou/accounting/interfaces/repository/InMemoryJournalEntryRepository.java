package tech.kayys.syirkah.accounting.interfaces.repository;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.syirkah.accounting.application.port.JournalEntryRepository;
import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;
import tech.kayys.syirkah.accounting.domain.model.JournalEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class InMemoryJournalEntryRepository implements JournalEntryRepository {
    private final Map<JournalEntryId, JournalEntry> store = new ConcurrentHashMap<>();

    @Override
    public Uni<Optional<JournalEntry>> findById(JournalEntryId id) {
        return Uni.createFrom().item(Optional.ofNullable(store.get(id)));
    }

    @Override
    public Uni<List<JournalEntry>> findAll() {
        return Uni.createFrom().item(new ArrayList<>(store.values()));
    }

    @Override
    public Uni<Void> save(JournalEntry entry) {
        store.put(entry.id(), entry);
        return Uni.createFrom().voidItem();
    }
}
