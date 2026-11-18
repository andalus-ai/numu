package tech.kayys.numou.identity.domain.clientcredential;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record OAuthClientCredentialRotated(
        UUID eventId,
        Instant occurredAt,
        OAuthClientCredentialId credentialId,
        UUID tenantId,
        UUID applicationId,
        String clientId,
        long credentialVersion
) implements DomainEvent {
    @Override
    public String eventType() {
        return "identity.oauth-client-credential.rotated";
    }
}
