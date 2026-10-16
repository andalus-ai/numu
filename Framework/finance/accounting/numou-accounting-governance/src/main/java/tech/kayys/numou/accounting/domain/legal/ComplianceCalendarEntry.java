package tech.kayys.syirkah.accounting.domain.legal;

import java.time.LocalDate;
import java.util.Objects;

public final class ComplianceCalendarEntry {
    private final ComplianceCalendarEntryId id;
    private final String contractId;
    private final String obligationId;
    private final String title;
    private final LocalDate dueDate;
    private ComplianceStatus status;
    private final int reminderDaysPrior;
    private final String penaltyDescription;

    public ComplianceCalendarEntry(ComplianceCalendarEntryId id, String contractId, String obligationId,
                                   String title, LocalDate dueDate, int reminderDaysPrior, String penaltyDescription) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.contractId = Objects.requireNonNull(contractId, "contractId must not be null");
        this.obligationId = Objects.requireNonNull(obligationId, "obligationId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.dueDate = Objects.requireNonNull(dueDate, "dueDate must not be null");
        this.reminderDaysPrior = Math.max(0, reminderDaysPrior);
        this.penaltyDescription = Objects.requireNonNullElse(penaltyDescription, "None");
        this.status = ComplianceStatus.PENDING;
    }

    public void markCompleted() {
        this.status = ComplianceStatus.COMPLETED;
    }

    public void evaluateOverdue(LocalDate asOfDate) {
        if (status == ComplianceStatus.PENDING && asOfDate.isAfter(dueDate)) {
            this.status = ComplianceStatus.OVERDUE;
        }
    }

    public ComplianceCalendarEntryId id() { return id; }
    public String contractId() { return contractId; }
    public String obligationId() { return obligationId; }
    public String title() { return title; }
    public LocalDate dueDate() { return dueDate; }
    public ComplianceStatus status() { return status; }
    public int reminderDaysPrior() { return reminderDaysPrior; }
    public String penaltyDescription() { return penaltyDescription; }
}
