package tech.kayys.syirkah.accounting.application.api.command;

import tech.kayys.syirkah.accounting.application.cqrs.Command;
import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;

import java.util.Objects;

public record ReverseJournalEntryCommand(
        JournalEntryId journalEntryId,
        String reversedBy,
        String reason
) implements Command {
    public ReverseJournalEntryCommand {
        Objects.requireNonNull(journalEntryId, "journalEntryId cannot be null");
        Objects.requireNonNull(reversedBy, "reversedBy cannot be null");
    }
}
