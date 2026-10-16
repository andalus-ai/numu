package tech.kayys.syirkah.accounting.domain.model;

import tech.kayys.syirkah.accounting.domain.dimension.DimensionValue;
import tech.kayys.syirkah.accounting.domain.event.*;
import tech.kayys.syirkah.accounting.domain.identifier.AccountId;
import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;
import tech.kayys.syirkah.accounting.domain.islamic.ShariaContractType;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerAware;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantAware;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class JournalEntry extends AbstractAggregateRoot<JournalEntryId> implements TenantAware, LedgerAware {
    public enum EntryStatus {
        DRAFT,
        PENDING_APPROVAL,
        APPROVED,
        POSTED,
        REVERSED
    }

    public record JournalLine(
            AccountId accountId,
            Money debit,
            Money credit,
            String description,
            ShariaContractType contractType,
            Money baseDebit,
            Money baseCredit,
            List<DimensionValue> dimensions
    ) implements ValueObject {
        public JournalLine(AccountId accountId, Money debit, Money credit, String description) {
            this(accountId, debit, credit, description, ShariaContractType.NONE, debit, credit, Collections.emptyList());
        }

        public JournalLine(AccountId accountId, Money debit, Money credit, String description, ShariaContractType contractType) {
            this(accountId, debit, credit, description, contractType, debit, credit, Collections.emptyList());
        }
    }

    private final JournalEntryId id;
    private final TenantRef tenantId;
    private final LedgerId ledgerId;
    private String entryNumber;
    private Instant entryDate;
    private String description;
    private EntryStatus status;
    private ShariaContractType shariaContractType = ShariaContractType.NONE;

    // Maker-Checker audit metadata
    private String createdBy;
    private String submittedBy;
    private String approvedBy;
    private String postedBy;

    // Storno reversal metadata
    private JournalEntryId reversalOfEntryId;
    private Instant reversalDate;
    private String reversedBy;

    private final List<JournalLine> lines = new ArrayList<>();

    public JournalEntry(
            JournalEntryId id,
            TenantRef tenantId,
            LedgerId ledgerId,
            String entryNumber,
            Instant entryDate,
            String description
    ) {
        super(id);
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId != null ? tenantId : TenantRef.defaultTenant(), "tenantId cannot be null");
        this.ledgerId = Objects.requireNonNull(ledgerId != null ? ledgerId : LedgerId.primary(), "ledgerId cannot be null");
        this.entryNumber = Objects.requireNonNull(entryNumber, "entryNumber cannot be null");
        this.entryDate = Objects.requireNonNull(entryDate, "entryDate cannot be null");
        this.description = description;
        this.status = EntryStatus.DRAFT;
    }

    public JournalEntry(JournalEntryId id, String entryNumber, Instant entryDate, String description) {
        this(id, TenantRef.defaultTenant(), LedgerId.primary(), entryNumber, entryDate, description);
    }

    @Override
    public JournalEntryId id() { return id; }
    public JournalEntryId getId() { return id; }

    @Override
    public TenantRef tenantId() { return tenantId; }

    @Override
    public LedgerId ledgerId() { return ledgerId; }

    public void addLine(AccountId accountId, Money debit, Money credit, String desc) {
        addLine(accountId, debit, credit, desc, this.shariaContractType);
    }

    public void addLine(AccountId accountId, Money debit, Money credit, String desc, ShariaContractType contractType) {
        addLine(new JournalLine(accountId, debit, credit, desc, contractType, debit, credit, Collections.emptyList()));
    }

    public void addLine(JournalLine line) {
        if (status != EntryStatus.DRAFT) {
            throw new IllegalStateException("Cannot add lines to non-draft journal entry: status is " + status);
        }
        lines.add(Objects.requireNonNull(line, "line cannot be null"));
    }

    public void submitForApproval(String maker) {
        if (status != EntryStatus.DRAFT) {
            throw new IllegalStateException("Only draft entries can be submitted for approval; status: " + status);
        }
        if (lines.isEmpty()) {
            throw new IllegalStateException("Cannot submit empty journal entry");
        }
        validateBalanceInvariant();
        this.status = EntryStatus.PENDING_APPROVAL;
        this.submittedBy = maker;
    }

    public void approve(String checker) {
        if (status != EntryStatus.PENDING_APPROVAL && status != EntryStatus.DRAFT) {
            throw new IllegalStateException("Entry not in approvable state; current status: " + status);
        }
        validateBalanceInvariant();
        this.status = EntryStatus.APPROVED;
        this.approvedBy = checker;
        raise(JournalEntryApproved.of(tenantId, ledgerId, id, checker, null, null));
    }

    public void post() {
        post("system");
    }

    public void post(String postedBy) {
        if (status != EntryStatus.DRAFT && status != EntryStatus.APPROVED) {
            throw new IllegalStateException("Only draft or approved entries can be posted; current status is " + status);
        }
        validateBalanceInvariant();

        this.status = EntryStatus.POSTED;
        this.postedBy = postedBy;
        raise(JournalEntryPosted.of(tenantId, ledgerId, id, entryNumber, postedBy, null, null));
    }

    private void validateBalanceInvariant() {
        if (lines.isEmpty()) {
            throw new IllegalStateException("Cannot post or approve an empty journal entry");
        }

        Money totalDebit = null;
        Money totalCredit = null;

        for (JournalLine line : lines) {
            if (totalDebit == null) {
                totalDebit = line.debit();
                totalCredit = line.credit();
            } else {
                totalDebit = totalDebit.add(line.debit());
                totalCredit = totalCredit.add(line.credit());
            }
        }

        if (totalDebit == null || !totalDebit.equals(totalCredit)) {
            throw new IllegalStateException(
                    "Double-entry balance violation: Total debits (" + totalDebit + 
                    ") must equal total credits (" + totalCredit + ")");
        }

        if (totalDebit.isZero()) {
            throw new IllegalStateException("Cannot post a zero-value journal entry");
        }
    }

    public void markReversed(JournalEntryId reversalId, Instant at, String by) {
        if (this.status != EntryStatus.POSTED) {
            throw new IllegalStateException("Only posted entries can be reversed; current status: " + status);
        }
        this.status = EntryStatus.REVERSED;
        this.reversalOfEntryId = reversalId;
        this.reversalDate = at;
        this.reversedBy = by;
    }

    public JournalEntry createStornoReversal(JournalEntryId newId, String newEntryNumber, Instant newEntryDate, String by, String reason) {
        if (this.status != EntryStatus.POSTED) {
            throw new IllegalStateException("Cannot create reversal for unposted entry");
        }
        JournalEntry reversal = new JournalEntry(
                newId,
                tenantId,
                ledgerId,
                newEntryNumber,
                newEntryDate,
                "REVERSAL [" + this.entryNumber + "]: " + (reason != null ? reason : this.description)
        );
        reversal.shariaContractType = this.shariaContractType;
        reversal.reversalOfEntryId = this.id;
        reversal.reversedBy = by;
        reversal.reversalDate = newEntryDate;

        for (JournalLine line : this.lines) {
            reversal.addLine(new JournalLine(
                    line.accountId(),
                    line.credit(),
                    line.debit(),
                    "Reversal of line: " + line.description(),
                    line.contractType(),
                    line.baseCredit(),
                    line.baseDebit(),
                    line.dimensions()
            ));
        }

        raise(JournalEntryReversed.of(tenantId, ledgerId, id, newId, by, reason, null, null));
        return reversal;
    }

    public String getEntryNumber() { return entryNumber; }
    public Instant getEntryDate() { return entryDate; }
    public String getDescription() { return description; }
    public EntryStatus getStatus() { return status; }
    public List<JournalLine> getLines() { return Collections.unmodifiableList(lines); }
    public ShariaContractType getShariaContractType() { return shariaContractType; }
    public void setShariaContractType(ShariaContractType type) { 
        if (status != EntryStatus.DRAFT) {
            throw new IllegalStateException("Cannot modify contract type of non-draft entry");
        }
        this.shariaContractType = type != null ? type : ShariaContractType.NONE; 
    }
    public JournalEntryId getReversalOfEntryId() { return reversalOfEntryId; }
    public Instant getReversalDate() { return reversalDate; }
    public String getReversedBy() { return reversedBy; }
    public String getCreatedBy() { return createdBy; }
    public String getSubmittedBy() { return submittedBy; }
    public String getApprovedBy() { return approvedBy; }
    public String getPostedBy() { return postedBy; }
    public boolean isReversal() { return reversalOfEntryId != null; }
}
