package tech.kayys.numou.billing.dunning.model;

import tech.kayys.numou.billing.domain.valueobject.DunningAction;
import tech.kayys.numou.billing.domain.valueobject.DunningLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class DunningCase {

    public enum CaseStatus {
        ACTIVE,
        PAUSED,
        RESOLVED,
        WRITTEN_OFF
    }

    private String caseId;
    private String scheduleId;
    private String customerId;
    private String invoiceId;
    private BigDecimal overdueAmount;
    private String currency;
    private DunningLevel currentLevel;
    private CaseStatus status;
    private int attemptCount;
    private Instant createdAt;
    private Instant lastActionDate;
    private Instant nextActionDate;
    private String resolutionNotes;

    public DunningCase() {}

    public DunningCase(
            String scheduleId,
            String customerId,
            String invoiceId,
            BigDecimal overdueAmount,
            String currency) {
        this.caseId = "DC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.scheduleId = scheduleId;
        this.customerId = customerId;
        this.invoiceId = invoiceId;
        this.overdueAmount = overdueAmount;
        this.currency = currency != null ? currency : "USD";
        this.currentLevel = DunningLevel.create(1, "Initial Reminder", 3, DunningAction.EMAIL_REMINDER, "email_reminder_1");
        this.status = CaseStatus.ACTIVE;
        this.attemptCount = 0;
        this.createdAt = Instant.now();
        this.lastActionDate = Instant.now();
        this.nextActionDate = Instant.now();
    }

    public void advanceLevel(DunningLevel nextLevel, Instant nextDate) {
        this.currentLevel = nextLevel;
        this.attemptCount++;
        this.lastActionDate = Instant.now();
        this.nextActionDate = nextDate;
    }

    public void resolve(String notes) {
        this.status = CaseStatus.RESOLVED;
        this.resolutionNotes = notes;
    }

    public void writeOff(String notes) {
        this.status = CaseStatus.WRITTEN_OFF;
        this.resolutionNotes = notes;
    }

    public String getCaseId() { return caseId; }
    public String getScheduleId() { return scheduleId; }
    public String getCustomerId() { return customerId; }
    public String getInvoiceId() { return invoiceId; }
    public BigDecimal getOverdueAmount() { return overdueAmount; }
    public String getCurrency() { return currency; }
    public DunningLevel getCurrentLevel() { return currentLevel; }
    public CaseStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActionDate() { return lastActionDate; }
    public Instant getNextActionDate() { return nextActionDate; }
    public String getResolutionNotes() { return resolutionNotes; }
}
