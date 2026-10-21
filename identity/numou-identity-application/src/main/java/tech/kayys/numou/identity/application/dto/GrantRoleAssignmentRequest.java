package tech.kayys.numou.identity.application.dto;

import java.time.Instant;
import java.util.UUID;

public record GrantRoleAssignmentRequest(
        String principalType,
        UUID principalId,
        UUID roleId,
        String scopeType,
        String scopePath,
        Instant expiresAt
) {}
