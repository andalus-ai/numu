package tech.kayys.numou.accounting.application.port;

import tech.kayys.numou.accounting.domain.identifier.JournalEntryId;
import tech.kayys.numou.accounting.domain.model.JournalEntry;
import io.smallrye.mutiny.Uni;
import java.util.List;
import java.util.Optional;

public interface JournalEntryRepository {
    Uni<Optional<JournalEntry>> findById(JournalEntryId id);
    Uni<List<JournalEntry>> findAll();
    Uni<Void> save(JournalEntry entry);
}
