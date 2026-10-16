package tech.kayys.syirkah.construction.domain.warranty;

import tech.kayys.syirkah.construction.domain.warranty.event.WarrantyStarted;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public final class Warranty extends AbstractAggregateRoot<WarrantyId> {
    private final UUID projectId;
    private final LocalDate startDate;
    private final LocalDate validUntil;
    private WarrantyStatus status;

    private Warranty(WarrantyId id, UUID projectId, LocalDate startDate, LocalDate validUntil) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId);
        this.startDate = Objects.requireNonNull(startDate);
        this.validUntil = Objects.requireNonNull(validUntil);
        this.status = WarrantyStatus.ACTIVE;
    }

    public static Warranty start(UUID projectId, LocalDate startDate, LocalDate validUntil) {
        var warranty = new Warranty(WarrantyId.generate(), projectId, startDate, validUntil);
        warranty.raise(new WarrantyStarted(UUID.randomUUID(), Instant.now(), warranty.id().value(), projectId, validUntil));
        return warranty;
    }

    public UUID projectId() { return projectId; }
    public LocalDate startDate() { return startDate; }
    public LocalDate validUntil() { return validUntil; }
    public WarrantyStatus status() { return status; }
}
