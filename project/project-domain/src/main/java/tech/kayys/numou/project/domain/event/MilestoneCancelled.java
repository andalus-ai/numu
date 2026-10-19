package tech.kayys.numou.project.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.project.domain.milestone.ProjectMilestoneId;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.time.Instant;
import java.util.UUID;

public record MilestoneCancelled(
        UUID eventId,
        Instant occurredAt,
        ProjectMilestoneId milestoneId,
        ProjectId projectId
) implements DomainEvent {

    @Override
    public String eventType() {
        return "project.milestone-cancelled";
    }
}
