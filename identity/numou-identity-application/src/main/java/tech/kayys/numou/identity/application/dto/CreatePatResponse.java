package tech.kayys.numou.identity.application.dto;

import java.time.Instant;
import java.util.UUID;

public record CreatePatResponse(
        UUID id,
        String label,
        String token,
        String displayPrefix,
        Instant expiresAt
) {}
