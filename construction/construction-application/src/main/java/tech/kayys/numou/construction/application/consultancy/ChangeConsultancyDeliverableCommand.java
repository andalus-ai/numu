package tech.kayys.numou.construction.application.consultancy;

import java.util.Objects;
import java.util.UUID;

/** Deliverable lifecycle intent; tenant and actor identity must come from trusted authentication context. */
public record ChangeConsultancyDeliverableCommand(
        ConsultancyTenantContext context,
        String idempotencyKey,
        UUID deliverableId,
        Action action,
        String title,
        String description,
        String reviewComment) {
    public ChangeConsultancyDeliverableCommand {
        Objects.requireNonNull(context, "Trusted tenant context is required");
        requireText(idempotencyKey, "Idempotency key");
        Objects.requireNonNull(deliverableId, "Deliverable id is required");
        Objects.requireNonNull(action, "Lifecycle action is required");
        switch (action) {
            case REVISE -> {
                requireText(title, "Title");
                requireText(description, "Description");
                title = title.trim();
                description = description.trim();
            }
            case REJECT -> requireText(reviewComment, "Rejection reason");
            default -> { }
        }
        idempotencyKey = idempotencyKey.trim();
        if (title != null) title = title.trim();
        if (description != null) description = description.trim();
        if (reviewComment != null) reviewComment = reviewComment.trim();
    }

    public enum Action {
        SUBMIT("Submitted"),
        ACCEPT("Accepted"),
        REJECT("Rejected"),
        REVISE("Revised");

        private final String eventSuffix;
        Action(String eventSuffix) { this.eventSuffix = eventSuffix; }
        public String eventSuffix() { return eventSuffix; }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
