package tech.kayys.numou.construction.domain.consultancy;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import java.util.Objects;
import java.util.UUID;

/**
 * Versioned consultant output with an explicit client review gate. The domain
 * records organization-level separation; application authorization must still
 * authenticate the acting person and prove their permission to act for that
 * organization.
 */
public final class ConsultancyDeliverable extends AbstractAggregateRoot<ConsultancyDeliverableId> {
    private final ConsultancyEngagementId engagementId;
    private final UUID clientOrganizationId;
    private final UUID consultantOrganizationId;
    private final String code;
    private String title;
    private String description;
    private int revision;
    private ConsultancyDeliverableStatus status;
    private UUID reviewedByOrganizationId;
    private String reviewComment;

    private ConsultancyDeliverable(
            ConsultancyDeliverableId id,
            ConsultancyEngagementId engagementId,
            UUID clientOrganizationId,
            UUID consultantOrganizationId,
            String code,
            String title,
            String description) {
        super(Objects.requireNonNull(id, "Deliverable id cannot be null"));
        this.engagementId = Objects.requireNonNull(engagementId, "Engagement id cannot be null");
        this.clientOrganizationId = Objects.requireNonNull(clientOrganizationId, "Client organization cannot be null");
        this.consultantOrganizationId = Objects.requireNonNull(consultantOrganizationId, "Consultant organization cannot be null");
        if (clientOrganizationId.equals(consultantOrganizationId)) {
            throw new IllegalArgumentException("Client and consultant organizations must be different");
        }
        this.code = requireText(code, "Deliverable code");
        this.title = requireText(title, "Deliverable title");
        this.description = requireText(description, "Deliverable description");
        this.revision = 1;
        this.status = ConsultancyDeliverableStatus.DRAFT;
    }

    public static ConsultancyDeliverable create(
            ConsultancyEngagementId engagementId,
            UUID clientOrganizationId,
            UUID consultantOrganizationId,
            String code,
            String title,
            String description) {
        return new ConsultancyDeliverable(
                ConsultancyDeliverableId.generate(),
                engagementId,
                clientOrganizationId,
                consultantOrganizationId,
                code,
                title,
                description);
    }

    /** Restores persisted state without replaying lifecycle transitions. */
    public static ConsultancyDeliverable reconstitute(
            ConsultancyDeliverableId id,
            ConsultancyEngagementId engagementId,
            UUID clientOrganizationId,
            UUID consultantOrganizationId,
            String code,
            String title,
            String description,
            int revision,
            ConsultancyDeliverableStatus status,
            UUID reviewedByOrganizationId,
            String reviewComment) {
        if (revision < 1) {
            throw new IllegalArgumentException("Deliverable revision must be positive");
        }
        Objects.requireNonNull(status, "Deliverable status cannot be null");
        ConsultancyDeliverable deliverable = new ConsultancyDeliverable(
                id, engagementId, clientOrganizationId, consultantOrganizationId,
                code, title, description);
        if ((status == ConsultancyDeliverableStatus.ACCEPTED || status == ConsultancyDeliverableStatus.REJECTED)
                && !Objects.equals(clientOrganizationId, reviewedByOrganizationId)) {
            throw new IllegalArgumentException("Reviewed deliverable must record the client organization");
        }
        if (status == ConsultancyDeliverableStatus.REJECTED
                && (reviewComment == null || reviewComment.isBlank())) {
            throw new IllegalArgumentException("Rejected deliverable must retain its review reason");
        }
        if ((status == ConsultancyDeliverableStatus.DRAFT || status == ConsultancyDeliverableStatus.SUBMITTED)
                && (reviewedByOrganizationId != null || reviewComment != null)) {
            throw new IllegalArgumentException("Unreviewed deliverable cannot retain review data");
        }
        deliverable.title = requireText(title, "Deliverable title");
        deliverable.description = requireText(description, "Deliverable description");
        deliverable.revision = revision;
        deliverable.status = status;
        deliverable.reviewedByOrganizationId = reviewedByOrganizationId;
        deliverable.reviewComment = normalizeOptionalText(reviewComment);
        return deliverable;
    }

    public void submit() {
        if (status != ConsultancyDeliverableStatus.DRAFT) {
            throw new IllegalStateException("Only a draft deliverable can be submitted");
        }
        status = ConsultancyDeliverableStatus.SUBMITTED;
        reviewedByOrganizationId = null;
        reviewComment = null;
    }

    public void accept(UUID reviewerOrganizationId, String comment) {
        requireClientReviewer(reviewerOrganizationId);
        if (status != ConsultancyDeliverableStatus.SUBMITTED) {
            throw new IllegalStateException("Only a submitted deliverable can be accepted");
        }
        reviewedByOrganizationId = reviewerOrganizationId;
        reviewComment = normalizeOptionalText(comment);
        status = ConsultancyDeliverableStatus.ACCEPTED;
    }

    public void reject(UUID reviewerOrganizationId, String reason) {
        requireClientReviewer(reviewerOrganizationId);
        if (status != ConsultancyDeliverableStatus.SUBMITTED) {
            throw new IllegalStateException("Only a submitted deliverable can be rejected");
        }
        reviewedByOrganizationId = reviewerOrganizationId;
        reviewComment = requireText(reason, "Rejection reason");
        status = ConsultancyDeliverableStatus.REJECTED;
    }

    /** A rejected deliverable must be revised and resubmitted for a new review. */
    public void revise(String title, String description) {
        if (status != ConsultancyDeliverableStatus.REJECTED) {
            throw new IllegalStateException("Only a rejected deliverable can be revised");
        }
        this.title = requireText(title, "Deliverable title");
        this.description = requireText(description, "Deliverable description");
        this.revision = Math.incrementExact(revision);
        this.status = ConsultancyDeliverableStatus.DRAFT;
        this.reviewedByOrganizationId = null;
        this.reviewComment = null;
    }

    private void requireClientReviewer(UUID reviewerOrganizationId) {
        Objects.requireNonNull(reviewerOrganizationId, "Reviewer organization cannot be null");
        if (!clientOrganizationId.equals(reviewerOrganizationId)) {
            throw new IllegalArgumentException("Deliverable review must be performed by the client organization");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public ConsultancyEngagementId engagementId() { return engagementId; }
    public UUID clientOrganizationId() { return clientOrganizationId; }
    public UUID consultantOrganizationId() { return consultantOrganizationId; }
    public String code() { return code; }
    public String title() { return title; }
    public String description() { return description; }
    public int revision() { return revision; }
    public ConsultancyDeliverableStatus status() { return status; }
    public UUID reviewedByOrganizationId() { return reviewedByOrganizationId; }
    public String reviewComment() { return reviewComment; }
}
