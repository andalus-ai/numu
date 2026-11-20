package tech.kayys.numou.accounting.application.api.command;

import tech.kayys.numou.accounting.domain.identifier.JournalEntryId;
import tech.kayys.numou.accounting.application.cqrs.Command;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Command to post a journal entry within an explicit tenant and ledger scope. */
public record PostJournalEntryCommand(
        JournalEntryId journalEntryId,
        TenantRef tenantId,
        LedgerId ledgerId,
        String description,
        String referenceNumber,
        List<JournalLineCommand> lines,
        String sourceType,
        String sourceId,
        String postedBy
) implements Command {

    /** Source-compatible constructor; new callers should supply the scope explicitly. */
    @Deprecated(forRemoval = false)
    public PostJournalEntryCommand(JournalEntryId journalEntryId, String description, String referenceNumber,
                                   List<JournalLineCommand> lines, String sourceType, String sourceId, String postedBy) {
        this(journalEntryId, TenantRef.defaultTenant(), LedgerId.primary(), description, referenceNumber,
                lines, sourceType, sourceId, postedBy);
    }

    public PostJournalEntryCommand {
        tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        ledgerId = Objects.requireNonNull(ledgerId, "ledgerId cannot be null");
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description cannot be empty");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Journal entry must have at least one line");
        }
        lines = List.copyOf(lines);
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private JournalEntryId journalEntryId;
        private TenantRef tenantId = TenantRef.defaultTenant();
        private LedgerId ledgerId = LedgerId.primary();
        private String description;
        private String referenceNumber;
        private List<JournalLineCommand> lines;
        private String sourceType;
        private String sourceId;
        private String postedBy;

        public Builder journalEntryId(JournalEntryId value) { this.journalEntryId = value; return this; }
        public Builder tenantId(TenantRef value) { this.tenantId = value; return this; }
        public Builder ledgerId(LedgerId value) { this.ledgerId = value; return this; }
        public Builder description(String value) { this.description = value; return this; }
        public Builder referenceNumber(String value) { this.referenceNumber = value; return this; }
        public Builder lines(List<JournalLineCommand> value) { this.lines = value; return this; }
        public Builder sourceType(String value) { this.sourceType = value; return this; }
        public Builder sourceId(String value) { this.sourceId = value; return this; }
        public Builder postedBy(String value) { this.postedBy = value; return this; }

        public PostJournalEntryCommand build() {
            if (journalEntryId == null) journalEntryId = JournalEntryId.generate();
            return new PostJournalEntryCommand(journalEntryId, tenantId, ledgerId, description,
                    referenceNumber, lines, sourceType, sourceId, postedBy);
        }
    }

    public record JournalLineCommand(UUID accountId, String type, String amount,
                                     String currencyCode, String description) {
        public JournalLineCommand {
            if (accountId == null) throw new IllegalArgumentException("Account ID cannot be null");
            if (type == null || (!type.equals("DEBIT") && !type.equals("CREDIT"))) {
                throw new IllegalArgumentException("Type must be DEBIT or CREDIT");
            }
            if (amount == null || amount.trim().isEmpty()) throw new IllegalArgumentException("Amount is required");
            if (currencyCode == null || currencyCode.trim().isEmpty()) {
                throw new IllegalArgumentException("Currency code is required");
            }
        }
    }
}
