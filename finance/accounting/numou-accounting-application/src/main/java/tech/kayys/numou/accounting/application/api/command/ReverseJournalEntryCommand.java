package tech.kayys.numou.accounting.application.api.command;

import tech.kayys.numou.accounting.application.cqrs.Command;
import tech.kayys.numou.accounting.domain.identifier.JournalEntryId;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;

import java.util.Objects;

public record ReverseJournalEntryCommand(
        JournalEntryId journalEntryId,
        TenantRef tenantId,
        LedgerId ledgerId,
        String reversedBy,
        String reason
) implements Command {
    @Deprecated(forRemoval = false)
    public ReverseJournalEntryCommand(JournalEntryId journalEntryId, String reversedBy, String reason) {
        this(journalEntryId, TenantRef.defaultTenant(), LedgerId.primary(), reversedBy, reason);
    }

    public ReverseJournalEntryCommand {
        Objects.requireNonNull(journalEntryId, "journalEntryId cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(ledgerId, "ledgerId cannot be null");
        Objects.requireNonNull(reversedBy, "reversedBy cannot be null");
    }
}
