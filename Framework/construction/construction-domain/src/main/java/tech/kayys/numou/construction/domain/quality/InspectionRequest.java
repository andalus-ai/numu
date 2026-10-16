package tech.kayys.syirkah.construction.domain.quality;

import tech.kayys.syirkah.construction.domain.quality.event.InspectionRequested;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public final class InspectionRequest extends AbstractAggregateRoot<InspectionRequestId> {
    private final UUID siteId;
    private final UUID wbsNodeId;
    private final LocalDate scheduledDate;
    private final String locationDescription;
    private InspectionRequestStatus status;
    private QualityInspectionResult result;

    private InspectionRequest(InspectionRequestId id, UUID siteId, UUID wbsNodeId, LocalDate scheduledDate, String locationDescription) {
        super(id);
        this.siteId = Objects.requireNonNull(siteId);
        this.wbsNodeId = Objects.requireNonNull(wbsNodeId);
        this.scheduledDate = Objects.requireNonNull(scheduledDate);
        this.locationDescription = locationDescription;
        this.status = InspectionRequestStatus.REQUESTED;
    }

    public static InspectionRequest request(UUID siteId, UUID wbsNodeId, LocalDate scheduledDate, String locationDescription) {
        var req = new InspectionRequest(InspectionRequestId.generate(), siteId, wbsNodeId, scheduledDate, locationDescription);
        req.raise(new InspectionRequested(UUID.randomUUID(), Instant.now(), req.id().value(), siteId, wbsNodeId));
        return req;
    }

    public void completeInspection(QualityInspectionResult result) {
        this.result = Objects.requireNonNull(result);
        this.status = InspectionRequestStatus.INSPECTED;
    }

    public UUID siteId() { return siteId; }
    public UUID wbsNodeId() { return wbsNodeId; }
    public LocalDate scheduledDate() { return scheduledDate; }
    public String locationDescription() { return locationDescription; }
    public InspectionRequestStatus status() { return status; }
    public QualityInspectionResult result() { return result; }
}
