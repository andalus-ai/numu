package tech.kayys.numou.courier.application.port;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Resolves partner webhook key material from the host's secret manager.
 * Implementations must scope resolution by tenant and return a fresh byte array
 * owned by the caller; the authenticator clears that array after verification.
 */
@FunctionalInterface
public interface CourierWebhookSecretResolver {
    CompletionStage<byte[]> resolve(TenantId tenantId, UUID partnerId, String secretReference);
}
