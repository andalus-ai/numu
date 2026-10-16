package tech.kayys.syirkah.construction.domain.progress;

import tech.kayys.syirkah.construction.domain.progress.event.ProgressMeasurementApproved;
import tech.kayys.syirkah.construction.domain.progress.event.ProgressMeasurementRecorded;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public final class ProgressMeasurement extends AbstractAggregateRoot<ProgressMeasurementId> {
    private final UUID projectId;
    private final UUID boqId;
    private final UUID boqItemId;
    private final LocalDate measurementDate;
    private final MeasurementType type;
    private MeasurementQuantity quantity;
    private ProgressStatus status;
    private String remarks;

    private ProgressMeasurement(
            ProgressMeasurementId id,
            UUID projectId,
            UUID boqId,
            UUID boqItemId,
            LocalDate measurementDate,
            MeasurementType type,
            MeasurementQuantity quantity,
            String remarks
    ) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId, "Project id cannot be null");
        this.boqId = Objects.requireNonNull(boqId, "BOQ id cannot be null");
        this.boqItemId = Objects.requireNonNull(boqItemId, "BOQ item id cannot be null");
        this.measurementDate = Objects.requireNonNull(measurementDate, "Measurement date cannot be null");
        this.type = Objects.requireNonNull(type, "Type cannot be null");
        this.quantity = Objects.requireNonNull(quantity, "Quantity cannot be null");
        this.remarks = remarks;
        this.status = ProgressStatus.DRAFT;
    }

    public static ProgressMeasurement record(
            UUID projectId,
            UUID boqId,
            UUID boqItemId,
            LocalDate measurementDate,
            MeasurementType type,
            MeasurementQuantity quantity,
            String remarks
    ) {
        var measurement = new ProgressMeasurement(
                ProgressMeasurementId.generate(),
                projectId,
                boqId,
                boqItemId,
                measurementDate,
                type,
                quantity,
                remarks
        );
        measurement.raise(new ProgressMeasurementRecorded(
                UUID.randomUUID(),
                Instant.now(),
                measurement.id().value(),
                projectId,
                boqItemId
        ));
        return measurement;
    }

    public void submit() {
        if (status != ProgressStatus.DRAFT) throw new IllegalStateException("Only draft measurements can be submitted");
        status = ProgressStatus.SUBMITTED;
    }

    public void approve() {
        if (status != ProgressStatus.SUBMITTED) throw new IllegalStateException("Only submitted measurements can be approved");
        status = ProgressStatus.APPROVED;
        raise(new ProgressMeasurementApproved(
                UUID.randomUUID(),
                Instant.now(),
                id().value(),
                projectId,
                boqItemId
        ));
    }

    public void reject(String reason) {
        if (status != ProgressStatus.SUBMITTED) throw new IllegalStateException("Only submitted measurements can be rejected");
        this.status = ProgressStatus.REJECTED;
        this.remarks = reason;
    }

    public UUID projectId() { return projectId; }
    public UUID boqId() { return boqId; }
    public UUID boqItemId() { return boqItemId; }
    public LocalDate measurementDate() { return measurementDate; }
    public MeasurementType type() { return type; }
    public MeasurementQuantity quantity() { return quantity; }
    public ProgressStatus status() { return status; }
    public String remarks() { return remarks; }
}
