package tech.kayys.numou.construction.application.consultancy;

import java.util.Objects;
import java.util.UUID;

/** Tenant-scoped request for a deliverable timeline, acting on behalf of one engagement party. */
public record GetConsultancyDeliverableHistoryQuery(
        ConsultancyTenantContext context,
        UUID deliverableId,
        UUID organizationId) {
    public GetConsultancyDeliverableHistoryQuery {
        Objects.requireNonNull(context, "Trusted tenant context is required");
        Objects.requireNonNull(deliverableId, "Deliverable id is required");
        Objects.requireNonNull(organizationId, "Acting organization is required");
    }
}
