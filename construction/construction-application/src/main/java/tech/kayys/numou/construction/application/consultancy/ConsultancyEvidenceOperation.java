package tech.kayys.numou.construction.application.consultancy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Durable saga state for attaching a version-pinned document to a consultancy
 * deliverable. The operation is persisted by Construction before the Document
 * capability is called; it does not imply a cross-capability transaction.
 */
public record ConsultancyEvidenceOperation(
        UUID operationId,
        String tenantId,
        String idempotencyKey,
        UUID deliverableId,
        UUID documentId,
        UUID documentVersionId,
        String relationship,
        UUID requestedBy,
        UUID requestingOrganizationId,
        Status status,
        int attempts,
        String lastFailure,
        Instant createdAt,
        Instant updatedAt) {

    public ConsultancyEvidenceOperation {
        Objects.requireNonNull(operationId, "Operation id is required");
        tenantId = requireText(tenantId, "Tenant id");
        idempotencyKey = requireText(idempotencyKey, "Idempotency key");
        Objects.requireNonNull(deliverableId, "Deliverable id is required");
        Objects.requireNonNull(documentId, "Document id is required");
        Objects.requireNonNull(documentVersionId, "Document version id is required");
        relationship = requireText(relationship, "Relationship");
        Objects.requireNonNull(requestedBy, "Requesting actor is required");
        Objects.requireNonNull(requestingOrganizationId, "Requesting organization is required");
        Objects.requireNonNull(status, "Status is required");
        if (attempts < 0) {
            throw new IllegalArgumentException("Attempts cannot be negative");
        }
        Objects.requireNonNull(createdAt, "Creation time is required");
        Objects.requireNonNull(updatedAt, "Update time is required");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Update time cannot precede creation time");
        }
        if (status == Status.LINKED && lastFailure != null) {
            throw new IllegalArgumentException("Linked operation cannot retain a failure");
        }
    }

    public static ConsultancyEvidenceOperation pending(
            UUID operationId,
            String tenantId,
            String idempotencyKey,
            UUID deliverableId,
            UUID documentId,
            UUID documentVersionId,
            String relationship,
            UUID requestedBy,
            UUID requestingOrganizationId,
            Instant now) {
        return new ConsultancyEvidenceOperation(operationId, tenantId, idempotencyKey,
                deliverableId, documentId, documentVersionId, relationship, requestedBy,
                requestingOrganizationId, Status.PENDING, 0, null, now, now);
    }

    public ConsultancyEvidenceOperation linked(Instant now) {
        requireTransition(Status.LINKED);
        return copy(Status.LINKED, attempts + 1, null, now);
    }

    public ConsultancyEvidenceOperation retryableFailure(String reason, Instant now) {
        requireTransition(Status.RETRYABLE_FAILURE);
        return copy(Status.RETRYABLE_FAILURE, attempts + 1,
                requireText(reason, "Failure reason"), now);
    }

    public ConsultancyEvidenceOperation terminalFailure(String reason, Instant now) {
        requireTransition(Status.TERMINAL_FAILURE);
        return copy(Status.TERMINAL_FAILURE, attempts + 1,
                requireText(reason, "Failure reason"), now);
    }

    public ConsultancyEvidenceOperation retry(Instant now) {
        if (status != Status.RETRYABLE_FAILURE) {
            throw new IllegalStateException("Only retryable failures can be retried");
        }
        return copy(Status.PENDING, attempts, null, now);
    }

    private void requireTransition(Status target) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("Cannot transition " + status + " to " + target);
        }
    }

    private ConsultancyEvidenceOperation copy(Status next, int nextAttempts,
            String failure, Instant now) {
        Objects.requireNonNull(now, "Update time is required");
        if (now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Update time cannot move backwards");
        }
        return new ConsultancyEvidenceOperation(operationId, tenantId, idempotencyKey,
                deliverableId, documentId, documentVersionId, relationship, requestedBy, requestingOrganizationId,
                next, nextAttempts, failure, createdAt, now);
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    public enum Status {
        PENDING,
        LINKED,
        RETRYABLE_FAILURE,
        TERMINAL_FAILURE
    }
}
