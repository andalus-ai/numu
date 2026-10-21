package tech.kayys.numou.identity.domain.role.assignment;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.identity.domain.role.RoleId;
import tech.kayys.numou.identity.domain.user.UserId;

import java.time.Instant;
import java.util.UUID;

public record RoleAssignmentChanged(
        UUID eventId,
        Instant occurredAt,
        RoleAssignmentId assignmentId,
        String principalType,
        UUID principalId,
        RoleId roleId,
        String scopePath,
        UserId actorUserId,
        boolean granted
) implements DomainEvent {

    @Override
    public String eventType() {
        return granted ? "identity.role-assignment.granted" : "identity.role-assignment.revoked";
    }
}
