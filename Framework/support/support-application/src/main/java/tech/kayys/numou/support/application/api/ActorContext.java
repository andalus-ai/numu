package tech.kayys.numou.support.application.api;

import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;

import java.util.Objects;

public record ActorContext(
        TenantId tenantId,
        ActorType type,
        UserId userId,
        ParticipantId participantId
) {
    public ActorContext {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        if (type != ActorType.SYSTEM && userId == null && participantId == null) {
            throw new IllegalArgumentException("A non-system actor must have a user or participant identity");
        }
    }
}
