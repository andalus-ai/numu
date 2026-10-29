package tech.kayys.numou.construction.application.consultancy;

import io.smallrye.mutiny.Uni;

import java.util.Objects;
import java.util.UUID;

/**
 * Separate authorization boundary for attaching or reading document evidence.
 * Implementations must check the persisted deliverable party relationship and
 * the Document capability's access policy independently. A UUID is a reference,
 * never proof of access.
 */
public interface ConsultancyEvidenceAuthorization {
    Uni<Void> requireDeliverableAccess(DeliverableAccess request);

    Uni<Void> requireDocumentAccess(DocumentAccess request);

    record DeliverableAccess(
            String tenantId, UUID actorId, UUID organizationId, UUID deliverableId, Action action) {
        public DeliverableAccess {
            tenantId = requireTenant(tenantId);
            Objects.requireNonNull(actorId, "Actor id is required");
            Objects.requireNonNull(organizationId, "Organization id is required");
            Objects.requireNonNull(deliverableId, "Deliverable id is required");
            Objects.requireNonNull(action, "Action is required");
        }
    }

    record DocumentAccess(
            String tenantId, UUID actorId, UUID organizationId, UUID documentId,
            UUID documentVersionId, Action action) {
        public DocumentAccess {
            tenantId = requireTenant(tenantId);
            Objects.requireNonNull(actorId, "Actor id is required");
            Objects.requireNonNull(organizationId, "Organization id is required");
            Objects.requireNonNull(documentId, "Document id is required");
            Objects.requireNonNull(documentVersionId, "Document version id is required");
            Objects.requireNonNull(action, "Action is required");
        }
    }

    enum Action { ATTACH_EVIDENCE, VIEW_EVIDENCE }

    private static String requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant id is required");
        }
        return tenantId.trim();
    }
}
