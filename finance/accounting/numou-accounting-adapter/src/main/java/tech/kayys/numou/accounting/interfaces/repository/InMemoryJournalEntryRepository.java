package tech.kayys.numou.accounting.interfaces.repository;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.accounting.application.port.JournalEntryRepository;
import tech.kayys.numou.accounting.domain.identifier.JournalEntryId;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.model.JournalEntry;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class InMemoryJournalEntryRepository implements JournalEntryRepository {
    private final Map<JournalEntryId, JournalEntry> store = new ConcurrentHashMap<>();

    @Override
    public Uni<Optional<JournalEntry>> findById(JournalEntryId id) {
        return Uni.createFrom().item(Optional.ofNullable(store.get(id)));
    }

    @Override
    public Uni<Optional<JournalEntry>> findById(TenantRef tenantId, LedgerId ledgerId, JournalEntryId id) {
        return Uni.createFrom().item(Optional.ofNullable(store.get(id))
                .filter(entry -> entry.tenantId().equals(tenantId) && entry.ledgerId().equals(ledgerId)));
    }

    @Override
    public Uni<List<JournalEntry>> findAll() {
        return Uni.createFrom().item(new ArrayList<>(store.values()));
    }

    @Override
    public Uni<List<JournalEntry>> findAll(TenantRef tenantId, LedgerId ledgerId) {
        return Uni.createFrom().item(store.values().stream()
                .filter(entry -> entry.tenantId().equals(tenantId) && entry.ledgerId().equals(ledgerId))
                .collect(Collectors.toList()));
    }

    @Override
    public Uni<Void> save(JournalEntry entry) {
        store.put(entry.id(), entry);
        return Uni.createFrom().voidItem();
    }
}
