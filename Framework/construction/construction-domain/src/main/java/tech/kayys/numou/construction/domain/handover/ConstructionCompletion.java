package tech.kayys.syirkah.construction.domain.handover;

import tech.kayys.syirkah.construction.domain.handover.event.ConstructionHandedOver;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public final class ConstructionCompletion extends AbstractAggregateRoot<ConstructionCompletionId> {
    private final UUID projectId;
    private ConstructionCompletionStatus status;
    private LocalDate handoverDate;

    private ConstructionCompletion(ConstructionCompletionId id, UUID projectId) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId);
        this.status = ConstructionCompletionStatus.IN_PROGRESS;
    }

    public static ConstructionCompletion initiate(UUID projectId) {
        return new ConstructionCompletion(ConstructionCompletionId.generate(), projectId);
    }

    public void completeHandover(LocalDate date) {
        this.handoverDate = Objects.requireNonNull(date);
        this.status = ConstructionCompletionStatus.HANDED_OVER;
        raise(new ConstructionHandedOver(UUID.randomUUID(), Instant.now(), id().value(), projectId));
    }

    public UUID projectId() { return projectId; }
    public ConstructionCompletionStatus status() { return status; }
    public LocalDate handoverDate() { return handoverDate; }
}
