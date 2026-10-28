package tech.kayys.numou.construction.application.consultancy;

import java.util.Objects;
import java.util.UUID;

/** Lifecycle intent; tenant and actor identity must originate from trusted context. */
public record ChangeConsultancyEngagementCommand(
        ConsultancyTenantContext context,
        String idempotencyKey,
        UUID engagementId,
        Action action,
        String title,
        String scopeStatement,
        String reason) {
    public ChangeConsultancyEngagementCommand {
        Objects.requireNonNull(context, "Trusted tenant context is required");
        requireText(idempotencyKey, "Idempotency key");
        Objects.requireNonNull(engagementId, "Engagement id is required");
        Objects.requireNonNull(action, "Lifecycle action is required");
        switch (action) {
            case REVISE_PROPOSAL -> {
                requireText(title, "Title");
                requireText(scopeStatement, "Scope statement");
                title = title.trim();
                scopeStatement = scopeStatement.trim();
            }
            case CANCEL -> requireText(reason, "Cancellation reason");
            default -> { }
        }
        idempotencyKey = idempotencyKey.trim();
        if (title != null) title = title.trim();
        if (scopeStatement != null) scopeStatement = scopeStatement.trim();
        if (reason != null) reason = reason.trim();
    }

    public enum Action {
        PROPOSE(".Proposed"),
        REVISE_PROPOSAL(".ProposalRevised"),
        ACTIVATE(".Activated"),
        COMPLETE(".Completed"),
        CANCEL(".Cancelled");

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
