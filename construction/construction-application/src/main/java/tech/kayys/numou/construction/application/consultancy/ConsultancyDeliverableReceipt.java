package tech.kayys.numou.construction.application.consultancy;

import tech.kayys.numou.construction.domain.consultancy.ConsultancyDeliverableStatus;

import java.util.Objects;
import java.util.UUID;

/** Stable idempotent result of a deliverable lifecycle command. */
public record ConsultancyDeliverableReceipt(
        UUID deliverableId,
        UUID engagementId,
        int revision,
        ConsultancyDeliverableStatus status) {
    public ConsultancyDeliverableReceipt {
        Objects.requireNonNull(deliverableId, "Deliverable id is required");
        Objects.requireNonNull(engagementId, "Engagement id is required");
        Objects.requireNonNull(status, "Status is required");
        if (revision < 1) throw new IllegalArgumentException("Revision must be positive");
    }
}
