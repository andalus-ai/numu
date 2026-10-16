package tech.kayys.syirkah.accounting.domain.audit;

import java.time.LocalDate;
import java.util.Objects;

public final class AuditFollowUp {
    private final FollowUpId id;
    private final RecommendationId recommendationId;
    private final String reviewerUserId;
    private final LocalDate scheduledDate;
    private FollowUpStatus status;
    private String outcomeNotes;

    public AuditFollowUp(FollowUpId id,
                         RecommendationId recommendationId,
                         String reviewerUserId,
                         LocalDate scheduledDate) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.recommendationId = Objects.requireNonNull(recommendationId, "recommendationId must not be null");
        this.reviewerUserId = Objects.requireNonNull(reviewerUserId, "reviewerUserId must not be null");
        this.scheduledDate = Objects.requireNonNull(scheduledDate, "scheduledDate must not be null");
        this.status = FollowUpStatus.SCHEDULED;
        this.outcomeNotes = "";
    }

    public void complete(String outcomeNotes) {
        if (status == FollowUpStatus.COMPLETED || status == FollowUpStatus.ESCALATED) {
            throw new AuditViolationException("Follow-up already terminal: " + status);
        }
        this.status = FollowUpStatus.COMPLETED;
        this.outcomeNotes = outcomeNotes == null ? "" : outcomeNotes;
    }

    public void escalate(String reason) {
        if (status == FollowUpStatus.COMPLETED) {
            throw new AuditViolationException("Cannot escalate a completed follow-up");
        }
        this.status = FollowUpStatus.ESCALATED;
        this.outcomeNotes = reason == null ? "" : reason;
    }

    public FollowUpId id() { return id; }
    public RecommendationId recommendationId() { return recommendationId; }
    public String reviewerUserId() { return reviewerUserId; }
    public LocalDate scheduledDate() { return scheduledDate; }
    public FollowUpStatus status() { return status; }
    public String outcomeNotes() { return outcomeNotes; }
}
