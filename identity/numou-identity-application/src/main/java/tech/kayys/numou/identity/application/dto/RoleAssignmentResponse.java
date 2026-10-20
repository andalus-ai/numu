package tech.kayys.numou.identity.application.dto;

import tech.kayys.numou.identity.domain.role.assignment.RoleAssignment;

import java.time.Instant;
import java.util.UUID;

public record RoleAssignmentResponse(
        UUID id,
        String principalType,
        UUID principalId,
        UUID roleId,
        String scopeType,
        String scopePath,
        UUID grantedByUserId,
        Instant grantedAt,
        Instant expiresAt
) {
    public static RoleAssignmentResponse from(RoleAssignment assignment) {
        return new RoleAssignmentResponse(
                assignment.id().value(),
                assignment.principalType(),
                assignment.principalId(),
                assignment.roleId().value(),
                assignment.scopeType().name(),
                assignment.scopePath(),
                assignment.grantedByUserId() != null ? assignment.grantedByUserId().value() : null,
                assignment.grantedAt(),
                assignment.expiresAt()
        );
    }
}
