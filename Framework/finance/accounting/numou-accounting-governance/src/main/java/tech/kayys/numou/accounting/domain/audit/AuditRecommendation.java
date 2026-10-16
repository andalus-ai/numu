package tech.kayys.syirkah.accounting.domain.audit;

import java.time.LocalDate;
import java.util.Objects;

public final class AuditRecommendation {
    private final RecommendationId id;
    private final FindingId findingId;
    private final String action;
    private final String ownerUserId;
    private final LocalDate dueDate;
    private RecommendationStatus status;
    private String closureNotes;

    public AuditRecommendation(RecommendationId id,
                               FindingId findingId,
                               String action,
                               String ownerUserId,
                               LocalDate dueDate) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.findingId = Objects.requireNonNull(findingId, "findingId must not be null");
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.ownerUserId = Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        this.dueDate = Objects.requireNonNull(dueDate, "dueDate must not be null");
        this.status = RecommendationStatus.ISSUED;
        this.closureNotes = "";
    }

    public void accept(String actor) {
        if (status != RecommendationStatus.ISSUED) {
            throw new AuditViolationException("accept requires ISSUED; current=" + status);
        }
        this.status = RecommendationStatus.ACCEPTED;
    }

    public void reject(String actor, String reason) {
        if (status != RecommendationStatus.ISSUED) {
            throw new AuditViolationException("reject requires ISSUED; current=" + status);
        }
        this.status = RecommendationStatus.REJECTED;
        this.closureNotes = reason == null ? "" : reason;
    }

    public void markImplemented(String closureNotes) {
        if (status != RecommendationStatus.ACCEPTED && status != RecommendationStatus.OVERDUE) {
            throw new AuditViolationException("markImplemented requires ACCEPTED or OVERDUE; current=" + status);
        }
        this.status = RecommendationStatus.IMPLEMENTED;
        this.closureNotes = closureNotes == null ? "" : closureNotes;
    }

    public void markOverdue() {
        if (status == RecommendationStatus.IMPLEMENTED || status == RecommendationStatus.REJECTED) {
            return;
        }
        this.status = RecommendationStatus.OVERDUE;
    }

    public RecommendationId id() { return id; }
    public FindingId findingId() { return findingId; }
    public String action() { return action; }
    public String ownerUserId() { return ownerUserId; }
    public LocalDate dueDate() { return dueDate; }
    public RecommendationStatus status() { return status; }
    public String closureNotes() { return closureNotes; }
}
