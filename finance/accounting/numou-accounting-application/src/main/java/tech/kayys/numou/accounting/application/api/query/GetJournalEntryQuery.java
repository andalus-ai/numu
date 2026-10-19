package tech.kayys.numou.accounting.application.api.query;

import tech.kayys.numou.accounting.application.cqrs.Query;
import tech.kayys.numou.accounting.domain.identifier.JournalEntryId;
import tech.kayys.numou.accounting.domain.model.JournalEntry;

import java.util.Objects;
import java.util.Optional;

public record GetJournalEntryQuery(
        JournalEntryId id
) implements Query<Optional<JournalEntry>> {
    public GetJournalEntryQuery {
        Objects.requireNonNull(id, "id cannot be null");
    }
}
