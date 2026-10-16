package tech.kayys.syirkah.accounting.domain.audit;

import java.time.Instant;
import java.util.Objects;

public final class WorkingPaper {
    private final WorkingPaperId id;
    private final EngagementId engagementId;
    private final String title;
    private final String reference;
    private final String preparedBy;
    private final String documentId;
    private String reviewedBy;
    private Instant reviewedAt;

    public WorkingPaper(WorkingPaperId id,
                        EngagementId engagementId,
                        String title,
                        String reference,
                        String preparedBy,
                        String documentId) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.engagementId = Objects.requireNonNull(engagementId, "engagementId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.reference = Objects.requireNonNull(reference, "reference must not be null");
        this.preparedBy = Objects.requireNonNull(preparedBy, "preparedBy must not be null");
        this.documentId = Objects.requireNonNull(documentId, "documentId must not be null");
    }

    public void review(String reviewer) {
        if (reviewer == null || reviewer.isBlank()) {
            throw new AuditViolationException("reviewer must not be blank");
        }
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
    }

    public WorkingPaperId id() { return id; }
    public EngagementId engagementId() { return engagementId; }
    public String title() { return title; }
    public String reference() { return reference; }
    public String preparedBy() { return preparedBy; }
    public String documentId() { return documentId; }
    public String reviewedBy() { return reviewedBy; }
    public Instant reviewedAt() { return reviewedAt; }
    public boolean isReviewed() { return reviewedBy != null; }
}
