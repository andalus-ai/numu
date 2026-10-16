package tech.kayys.syirkah.project.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.project.domain.milestone.ProjectMilestoneId;
import tech.kayys.syirkah.project.domain.project.ProjectId;

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
