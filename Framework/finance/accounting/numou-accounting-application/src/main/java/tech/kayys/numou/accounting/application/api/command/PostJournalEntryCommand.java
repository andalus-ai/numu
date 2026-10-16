package tech.kayys.syirkah.accounting.application.api.command;

import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;
import tech.kayys.syirkah.accounting.application.cqrs.Command;

import java.util.List;
import java.util.UUID;

/**
 * Command to post a journal entry.
 */
public record PostJournalEntryCommand(
        JournalEntryId journalEntryId,
        String description,
        String referenceNumber,
        List<JournalLineCommand> lines,
        String sourceType,
        String sourceId,
        String postedBy
) implements Command {

    public PostJournalEntryCommand {
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description cannot be empty");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Journal entry must have at least one line");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private JournalEntryId journalEntryId;
        private String description;
        private String referenceNumber;
        private List<JournalLineCommand> lines;
        private String sourceType;
        private String sourceId;
        private String postedBy;

        public Builder journalEntryId(JournalEntryId journalEntryId) {
            this.journalEntryId = journalEntryId;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder referenceNumber(String referenceNumber) {
            this.referenceNumber = referenceNumber;
            return this;
        }

        public Builder lines(List<JournalLineCommand> lines) {
            this.lines = lines;
            return this;
        }

        public Builder sourceType(String sourceType) {
            this.sourceType = sourceType;
            return this;
        }

        public Builder sourceId(String sourceId) {
            this.sourceId = sourceId;
            return this;
        }

        public Builder postedBy(String postedBy) {
            this.postedBy = postedBy;
            return this;
        }

        public PostJournalEntryCommand build() {
            if (journalEntryId == null) {
                journalEntryId = JournalEntryId.generate();
            }
            return new PostJournalEntryCommand(
                journalEntryId, description, referenceNumber,
                lines, sourceType, sourceId, postedBy
            );
        }
    }

    /**
     * Journal line command.
     */
    public record JournalLineCommand(
            UUID accountId,
            String type, // DEBIT or CREDIT
            String amount,
            String currencyCode,
            String description
    ) {
        public JournalLineCommand {
            if (accountId == null) {
                throw new IllegalArgumentException("Account ID cannot be null");
            }
            if (type == null || (!type.equals("DEBIT") && !type.equals("CREDIT"))) {
                throw new IllegalArgumentException("Type must be DEBIT or CREDIT");
            }
            if (amount == null || amount.trim().isEmpty()) {
                throw new IllegalArgumentException("Amount is required");
            }
            if (currencyCode == null || currencyCode.trim().isEmpty()) {
                throw new IllegalArgumentException("Currency code is required");
            }
        }
    }
}