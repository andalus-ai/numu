package tech.kayys.numou.courier.application.security;

import tech.kayys.numou.courier.application.port.CourierWebhookSecretResolver;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Resolves tenant-scoped key material and verifies a raw partner webhook before parsing or persistence. */
public final class PartnerWebhookAuthenticator {
    private final CourierWebhookSecretResolver secrets;

    public PartnerWebhookAuthenticator(CourierWebhookSecretResolver secrets) {
        this.secrets = Objects.requireNonNull(secrets, "secrets");
    }

    public CompletionStage<Void> authenticate(TenantId tenantId, UUID partnerId, String secretReference,
                                               byte[] rawBody, String signatureHeader,
                                               Instant now, Duration tolerance) {
        if (tenantId == null || partnerId == null || secretReference == null || secretReference.isBlank()
                || rawBody == null || signatureHeader == null || now == null || tolerance == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("webhook authentication context is incomplete"));
        final CompletionStage<byte[]> resolved;
        try {
            resolved = secrets.resolve(tenantId, partnerId, secretReference);
            if (resolved == null)
                return CompletableFuture.failedFuture(new SecurityException("webhook secret resolution failed"));
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(new SecurityException("webhook secret resolution failed", failure));
        }
        return resolved.thenApply(secret -> {
            if (secret == null)
                throw new SecurityException("webhook secret resolution returned no key");
            try {
                PartnerWebhookSignatureVerifier.verify(rawBody, signatureHeader, secret, now, tolerance);
                return (Void) null;
            } finally {
                Arrays.fill(secret, (byte) 0);
            }
        });
    }
}
