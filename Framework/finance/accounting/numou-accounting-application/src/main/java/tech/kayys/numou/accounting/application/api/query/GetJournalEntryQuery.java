package tech.kayys.syirkah.accounting.application.api.query;

import tech.kayys.syirkah.accounting.application.cqrs.Query;
import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;
import tech.kayys.syirkah.accounting.domain.model.JournalEntry;

import java.util.Objects;
import java.util.Optional;

public record GetJournalEntryQuery(
        JournalEntryId id
) implements Query<Optional<JournalEntry>> {
    public GetJournalEntryQuery {
        Objects.requireNonNull(id, "id cannot be null");
    }
}
