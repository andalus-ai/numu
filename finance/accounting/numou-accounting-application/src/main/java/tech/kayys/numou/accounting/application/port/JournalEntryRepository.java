package tech.kayys.numou.accounting.application.port;

import tech.kayys.numou.accounting.domain.identifier.JournalEntryId;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.model.JournalEntry;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;
import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Persistence boundary for journal entries. Scoped methods are the required
 * application contract; adapter implementations should push scope into SQL.
 */
public interface JournalEntryRepository {
    Uni<Optional<JournalEntry>> findById(JournalEntryId id);
    Uni<List<JournalEntry>> findAll();
    Uni<Void> save(JournalEntry entry);

    default Uni<Optional<JournalEntry>> findById(TenantRef tenantId, LedgerId ledgerId, JournalEntryId id) {
        return findAll().map(entries -> entries.stream()
                .filter(entry -> entry.tenantId().equals(tenantId) && entry.ledgerId().equals(ledgerId))
                .filter(entry -> entry.id().equals(id))
                .findFirst());
    }

    default Uni<List<JournalEntry>> findAll(TenantRef tenantId, LedgerId ledgerId) {
        return findAll().map(entries -> entries.stream()
                .filter(entry -> entry.tenantId().equals(tenantId) && entry.ledgerId().equals(ledgerId))
                .collect(Collectors.toList()));
    }
}
