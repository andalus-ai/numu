package tech.kayys.numou.courier.application.security;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class PartnerWebhookAuthenticatorTest {
    @Test
    void resolvesKeyWithinTrustedTenantAndClearsResolvedKey() {
        TenantId tenant = TenantId.generate();
        UUID partner = UUID.randomUUID();
        byte[] key = "tenant-bound-secret-material-at-least-32".getBytes(StandardCharsets.UTF_8);
        byte[] body = "{\"id\":\"evt-42\"}".getBytes(StandardCharsets.UTF_8);
        PartnerWebhookAuthenticator authenticator = new PartnerWebhookAuthenticator((resolvedTenant, resolvedPartner, ref) -> {
            assertEquals(tenant, resolvedTenant);
            assertEquals(partner, resolvedPartner);
            assertEquals("secret/courier/partner-42", ref);
            return CompletableFuture.completedFuture(key);
        });

        // The cryptographic verifier is independently exercised; this assertion
        // proves the composition fails closed for an invalid signature and clears
        // the secret buffer even on failure.
        assertThrows(Exception.class, () -> authenticator.authenticate(
                tenant, partner, "secret/courier/partner-42", body,
                "t=1793613600,v1=" + "0".repeat(64),
                Instant.parse("2026-11-02T10:00:00Z"), Duration.ofMinutes(5)).toCompletableFuture().join());
        assertTrue(Arrays.equals(new byte[key.length], key));
    }

    @Test
    void rejectsMissingSecretAndResolverFailure() {
        PartnerWebhookAuthenticator missing = new PartnerWebhookAuthenticator((t, p, r) ->
                CompletableFuture.completedFuture(null));
        assertThrows(Exception.class, () -> missing.authenticate(TenantId.generate(), UUID.randomUUID(),
                "secret/ref", new byte[]{1}, "t=1,v1=" + "0".repeat(64),
                Instant.now(), Duration.ofMinutes(1)).toCompletableFuture().join());

        PartnerWebhookAuthenticator unavailable = new PartnerWebhookAuthenticator((t, p, r) ->
                CompletableFuture.failedFuture(new IllegalStateException("secret store unavailable")));
        assertThrows(Exception.class, () -> unavailable.authenticate(TenantId.generate(), UUID.randomUUID(),
                "secret/ref", new byte[]{1}, "t=1,v1=" + "0".repeat(64),
                Instant.now(), Duration.ofMinutes(1)).toCompletableFuture().join());
    }
}
