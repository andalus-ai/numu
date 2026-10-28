package tech.kayys.numou.construction.application.consultancy;

import java.util.Objects;
import java.util.UUID;

/** Write intent; tenant and actor identity come from trusted context, not this payload. */
public record CreateConsultancyEngagementCommand(
        ConsultancyTenantContext context,
        String idempotencyKey,
        UUID projectId,
        UUID clientOrganizationId,
        UUID consultantOrganizationId,
        String engagementNumber,
        String title,
        String scopeStatement) {
    public CreateConsultancyEngagementCommand {
        Objects.requireNonNull(context, "Trusted tenant context is required");
        requireText(idempotencyKey, "Idempotency key");
        Objects.requireNonNull(projectId, "Project id is required");
        Objects.requireNonNull(clientOrganizationId, "Client organization is required");
        Objects.requireNonNull(consultantOrganizationId, "Consultant organization is required");
        requireText(engagementNumber, "Engagement number");
        requireText(title, "Title");
        requireText(scopeStatement, "Scope statement");
        if (clientOrganizationId.equals(consultantOrganizationId)) {
            throw new IllegalArgumentException("Client and consultant organizations must differ");
        }
        idempotencyKey = idempotencyKey.trim();
        engagementNumber = engagementNumber.trim();
        title = title.trim();
        scopeStatement = scopeStatement.trim();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
