package tech.kayys.syirkah.construction.domain.document;

import tech.kayys.syirkah.construction.domain.document.event.DrawingCreated;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Drawing extends AbstractAggregateRoot<DrawingId> {
    private final UUID projectId;
    private final String drawingNumber;
    private String title;
    private String discipline;
    private String currentRevision;
    private DrawingStatus status;

    private Drawing(DrawingId id, UUID projectId, String drawingNumber, String title, String discipline, String currentRevision) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId);
        this.drawingNumber = Objects.requireNonNull(drawingNumber);
        this.title = Objects.requireNonNull(title);
        this.discipline = Objects.requireNonNull(discipline);
        this.currentRevision = Objects.requireNonNull(currentRevision);
        this.status = DrawingStatus.DRAFT;
    }

    public static Drawing create(UUID projectId, String drawingNumber, String title, String discipline, String revision) {
        var drawing = new Drawing(DrawingId.generate(), projectId, drawingNumber, title, discipline, revision);
        drawing.raise(new DrawingCreated(UUID.randomUUID(), Instant.now(), drawing.id().value(), projectId, drawingNumber));
        return drawing;
    }

    public void approveForConstruction() {
        this.status = DrawingStatus.APPROVED_FOR_CONSTRUCTION;
    }

    public UUID projectId() { return projectId; }
    public String drawingNumber() { return drawingNumber; }
    public String title() { return title; }
    public String discipline() { return discipline; }
    public String currentRevision() { return currentRevision; }
    public DrawingStatus status() { return status; }
}
