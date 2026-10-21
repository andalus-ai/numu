package tech.kayys.numou.identity.application.dto;

import tech.kayys.numou.identity.domain.serviceaccount.ServiceAccount;

import java.time.Instant;
import java.util.UUID;

public record ServiceAccountResponse(
        UUID id,
        String name,
        String description,
        UUID ownerUserId,
        boolean enabled,
        Instant createdAt
) {
    public static ServiceAccountResponse from(ServiceAccount sa) {
        return new ServiceAccountResponse(
                sa.id().value(),
                sa.name(),
                sa.description(),
                sa.ownerUserId() != null ? sa.ownerUserId().value() : null,
                sa.isEnabled(),
                sa.createdAt()
        );
    }
}
