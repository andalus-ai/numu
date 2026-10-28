package tech.kayys.numou.construction.domain.consultancy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable point-in-time snapshot of a consultancy deliverable lifecycle action. */
public record ConsultancyDeliverableHistoryEntry(
        UUID historyId, UUID deliverableId, UUID engagementId, int revision,
        ConsultancyDeliverableStatus status, Action action, String title, String description,
        UUID reviewedByOrganizationId, String reviewComment, UUID actorId, Instant occurredAt) {
    public ConsultancyDeliverableHistoryEntry {
        Objects.requireNonNull(historyId, "History id is required");
        Objects.requireNonNull(deliverableId, "Deliverable id is required");
        Objects.requireNonNull(engagementId, "Engagement id is required");
        if (revision < 1) throw new IllegalArgumentException("Revision must be positive");
        Objects.requireNonNull(status, "Status is required");
        Objects.requireNonNull(action, "Action is required");
        title = requireText(title, "Title");
        description = requireText(description, "Description");
        Objects.requireNonNull(actorId, "Actor id is required");
        Objects.requireNonNull(occurredAt, "Occurrence time is required");
        reviewComment = normalizeOptional(reviewComment);
        if ((status == ConsultancyDeliverableStatus.ACCEPTED || status == ConsultancyDeliverableStatus.REJECTED)
                && reviewedByOrganizationId == null) {
            throw new IllegalArgumentException("Reviewed snapshots must identify the reviewer organization");
        }
        if (status == ConsultancyDeliverableStatus.REJECTED
                && (reviewComment == null || reviewComment.isBlank())) {
            throw new IllegalArgumentException("Rejected snapshots must preserve the rejection reason");
        }
        if ((status == ConsultancyDeliverableStatus.DRAFT || status == ConsultancyDeliverableStatus.SUBMITTED)
                && (reviewedByOrganizationId != null || reviewComment != null)) {
            throw new IllegalArgumentException("Unreviewed snapshots cannot contain review metadata");
        }
    }
    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
    public enum Action { CREATED, SUBMITTED, ACCEPTED, REJECTED, REVISED }
}
