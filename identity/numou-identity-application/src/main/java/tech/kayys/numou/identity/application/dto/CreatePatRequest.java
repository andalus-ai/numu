package tech.kayys.numou.identity.application.dto;

import java.time.Instant;
import java.util.UUID;

public record CreatePatRequest(
        String principalType,
        UUID principalId,
        String label,
        Instant expiresAt
) {}
