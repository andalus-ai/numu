package tech.kayys.syirkah.accounting.application.api.command;

import tech.kayys.syirkah.accounting.application.cqrs.Command;
import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;

import java.util.Objects;

public record SubmitJournalEntryApprovalCommand(
        JournalEntryId journalEntryId,
        String submittedBy
) implements Command {
    public SubmitJournalEntryApprovalCommand {
        Objects.requireNonNull(journalEntryId, "journalEntryId cannot be null");
        Objects.requireNonNull(submittedBy, "submittedBy cannot be null");
    }
}
