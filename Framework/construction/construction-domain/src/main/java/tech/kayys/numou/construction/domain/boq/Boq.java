package tech.kayys.syirkah.construction.domain.boq;

import tech.kayys.syirkah.construction.domain.boq.event.BoqApproved;
import tech.kayys.syirkah.construction.domain.boq.event.BoqCreated;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Boq extends AbstractAggregateRoot<BoqId> {
    private final UUID projectId;
    private String name;
    private BoqStatus status;
    private int currentRevision;

    private Boq(BoqId id, UUID projectId, String name) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId, "Project id cannot be null");
        this.name = Objects.requireNonNull(name, "BOQ name cannot be blank");
        this.status = BoqStatus.DRAFT;
        this.currentRevision = 1;
    }

    public static Boq create(UUID projectId, String name) {
        var boq = new Boq(BoqId.generate(), projectId, name);
        boq.raise(new BoqCreated(UUID.randomUUID(), Instant.now(), boq.id().value(), projectId));
        return boq;
    }

    public void submitForReview() {
        if (status != BoqStatus.DRAFT) throw new IllegalStateException("Only draft BOQ can be submitted");
        status = BoqStatus.UNDER_REVIEW;
    }

    public void approve() {
        if (status != BoqStatus.UNDER_REVIEW) throw new IllegalStateException("Only BOQ under review can be approved");
        status = BoqStatus.APPROVED;
        raise(new BoqApproved(UUID.randomUUID(), Instant.now(), id().value(), projectId, currentRevision));
    }

    public UUID projectId() { return projectId; }
    public String name() { return name; }
    public BoqStatus status() { return status; }
    public int currentRevision() { return currentRevision; }
}
