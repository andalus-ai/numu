package tech.kayys.syirkah.construction.domain.quality;

import tech.kayys.syirkah.construction.domain.quality.event.NcrCreated;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class NonConformanceReport extends AbstractAggregateRoot<NonConformanceReportId> {
    private final UUID siteId;
    private final String ncrNumber;
    private final String description;
    private final NcrSeverity severity;
    private NcrStatus status;
    private String correctiveAction;

    private NonConformanceReport(NonConformanceReportId id, UUID siteId, String ncrNumber, String description, NcrSeverity severity) {
        super(id);
        this.siteId = Objects.requireNonNull(siteId);
        this.ncrNumber = Objects.requireNonNull(ncrNumber);
        this.description = Objects.requireNonNull(description);
        this.severity = Objects.requireNonNull(severity);
        this.status = NcrStatus.OPEN;
    }

    public static NonConformanceReport create(UUID siteId, String ncrNumber, String description, NcrSeverity severity) {
        var ncr = new NonConformanceReport(NonConformanceReportId.generate(), siteId, ncrNumber, description, severity);
        ncr.raise(new NcrCreated(UUID.randomUUID(), Instant.now(), ncr.id().value(), siteId, ncrNumber, severity));
        return ncr;
    }

    public void proposeCorrectiveAction(String correctiveAction) {
        this.correctiveAction = Objects.requireNonNull(correctiveAction);
        this.status = NcrStatus.CORRECTIVE_ACTION_PENDING;
    }

    public void close() {
        this.status = NcrStatus.CLOSED;
    }

    public UUID siteId() { return siteId; }
    public String ncrNumber() { return ncrNumber; }
    public String description() { return description; }
    public NcrSeverity severity() { return severity; }
    public NcrStatus status() { return status; }
    public String correctiveAction() { return correctiveAction; }
}
