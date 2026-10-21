package tech.kayys.numou.identity.application.dto;

import tech.kayys.numou.identity.domain.token.PersonalAccessToken;
import tech.kayys.numou.identity.domain.token.TokenStatus;

import java.time.Instant;
import java.util.UUID;

public record PatResponse(
        UUID id,
        String principalType,
        UUID principalId,
        String label,
        String displayPrefix,
        TokenStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant lastUsedAt
) {
    public static PatResponse from(PersonalAccessToken token) {
        return new PatResponse(
                token.id().value(),
                token.principalType(),
                token.principalId(),
                token.label(),
                token.displayPrefix(),
                token.status(),
                token.createdAt(),
                token.expiresAt(),
                token.lastUsedAt()
        );
    }
}
