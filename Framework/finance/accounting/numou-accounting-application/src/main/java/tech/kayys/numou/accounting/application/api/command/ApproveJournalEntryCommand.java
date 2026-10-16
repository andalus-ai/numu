package tech.kayys.numou.accounting.application.api.command;

import tech.kayys.numou.accounting.application.cqrs.Command;
import tech.kayys.numou.accounting.domain.identifier.JournalEntryId;

import java.util.Objects;

public record ApproveJournalEntryCommand(
        JournalEntryId journalEntryId,
        String approvedBy
) implements Command {
    public ApproveJournalEntryCommand {
        Objects.requireNonNull(journalEntryId, "journalEntryId cannot be null");
        Objects.requireNonNull(approvedBy, "approvedBy cannot be null");
    }
}
