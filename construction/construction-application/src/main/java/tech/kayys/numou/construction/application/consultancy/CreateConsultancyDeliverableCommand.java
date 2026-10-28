package tech.kayys.numou.construction.application.consultancy;

import java.util.Objects;
import java.util.UUID;

/** Creates a deliverable under an existing engagement; party identities come from the persisted engagement. */
public record CreateConsultancyDeliverableCommand(
        ConsultancyTenantContext context, String idempotencyKey, UUID engagementId,
        String code, String title, String description) {
    public CreateConsultancyDeliverableCommand {
        Objects.requireNonNull(context, "Trusted tenant context is required");
        requireText(idempotencyKey, "Idempotency key");
        Objects.requireNonNull(engagementId, "Engagement id is required");
        requireText(code, "Deliverable code");
        requireText(title, "Title");
        requireText(description, "Description");
        idempotencyKey = idempotencyKey.trim();
        code = code.trim();
        title = title.trim();
        description = description.trim();
    }
    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
