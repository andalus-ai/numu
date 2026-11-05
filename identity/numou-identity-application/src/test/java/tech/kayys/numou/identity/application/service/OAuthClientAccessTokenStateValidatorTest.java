package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.identity.application.port.OAuthClientCredentialRepository;
import tech.kayys.numou.identity.domain.clientcredential.OAuthClientCredential;
import tech.kayys.numou.identity.domain.clientcredential.OAuthClientCredentialId;
import tech.kayys.numou.identity.domain.clientcredential.OAuthClientCredentialStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OAuthClientAccessTokenStateValidatorTest {
    private static final Instant NOW = Instant.parse("2026-11-04T00:00:00Z");
    private final UUID tenant = UUID.randomUUID();
    private final UUID app = UUID.randomUUID();

    @Test
    void acceptsCurrentActiveCredentialWithMatchingClaims() {
        OAuthClientCredential credential = credential(1);
        var verified = validator(credential).validate("access", credential.clientId(),
                tenant.toString(), app.toString(), 1, Set.of("orders.read")).await().indefinitely();
        assertEquals(credential.clientId(), verified.clientId());
        assertEquals(tenant, verified.tenantId());
        assertEquals(app, verified.applicationId());
        assertEquals(1, verified.credentialVersion());
        assertEquals(Set.of("orders.read"), verified.scopes());
    }

    @Test
    void rejectsWrongTokenUseMalformedClaimsUnknownClientAndScopeEscalation() {
        OAuthClientCredential credential = credential(1);
        var validator = validator(credential);
        assertRejected(validator.validate("refresh", credential.clientId(), tenant.toString(), app.toString(), 1, Set.of("orders.read")));
        assertRejected(validator.validate("access", credential.clientId(), "not-a-uuid", app.toString(), 1, Set.of("orders.read")));
        assertRejected(validator.validate("access", "unknown", tenant.toString(), app.toString(), 1, Set.of("orders.read")));
        assertRejected(validator.validate("access", credential.clientId(), tenant.toString(), app.toString(), 1, Set.of("admin")));
    }

    @Test
    void rejectsTenantApplicationAndStaleVersionAfterRotation() {
        OAuthClientCredential credential = credential(1);
        var validator = validator(credential);
        assertRejected(validator.validate("access", credential.clientId(), UUID.randomUUID().toString(), app.toString(), 1, Set.of("orders.read")));
        assertRejected(validator.validate("access", credential.clientId(), tenant.toString(), UUID.randomUUID().toString(), 1, Set.of("orders.read")));
        credential.rotate("b".repeat(64), "next-prefix", NOW.plusSeconds(7200), NOW.minusSeconds(10));
        assertRejected(validator.validate("access", credential.clientId(), tenant.toString(), app.toString(), 1, Set.of("orders.read")));
        assertDoesNotThrow(() -> validator.validate("access", credential.clientId(), tenant.toString(), app.toString(), 2, Set.of("orders.read")).await().indefinitely());
    }

    @Test
    void rejectsRevokedAndExpiredCredentials() {
        OAuthClientCredential revoked = credential(1);
        revoked.revoke(NOW.minusSeconds(1));
        assertRejected(validator(revoked).validate("access", revoked.clientId(), tenant.toString(), app.toString(), 1, Set.of("orders.read")));
        OAuthClientCredential expired = OAuthClientCredential.issue(OAuthClientCredentialId.newId(), tenant, app,
                "nmc_expired_" + UUID.randomUUID().toString().replace("-", ""), "a".repeat(64), "prefix",
                Set.of("orders.read"), NOW.minusSeconds(1), NOW.minusSeconds(60));
        assertRejected(validator(expired).validate("access", expired.clientId(), tenant.toString(), app.toString(), 1, Set.of("orders.read")));
    }

    private OAuthClientCredential credential(long version) {
        return OAuthClientCredential.reconstitute(OAuthClientCredentialId.newId(), tenant, app,
                "nmc_test_" + UUID.randomUUID().toString().replace("-", ""), "a".repeat(64), "prefix",
                Set.of("orders.read", "orders.write"), OAuthClientCredentialStatus.ACTIVE, version,
                NOW.minusSeconds(60), NOW.plusSeconds(3600), null, null);
    }

    private OAuthClientAccessTokenStateValidator validator(OAuthClientCredential credential) {
        OAuthClientCredentialRepository repository = new OAuthClientCredentialRepository() {
            public Uni<Optional<OAuthClientCredential>> findById(UUID t, OAuthClientCredentialId id) { return Uni.createFrom().item(Optional.empty()); }
            public Uni<Optional<OAuthClientCredential>> findByClientId(UUID t, String id) { return Uni.createFrom().item(Optional.empty()); }
            public Uni<Optional<OAuthClientCredential>> findByClientId(String id) {
                return Uni.createFrom().item(id.equals(credential.clientId()) ? Optional.of(credential) : Optional.empty());
            }
            public Uni<Void> save(OAuthClientCredential value) { return Uni.createFrom().voidItem(); }
        };
        return new OAuthClientAccessTokenStateValidator(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static void assertRejected(Uni<?> result) {
        assertThrows(OAuthTokenRejectedException.class, () -> result.await().indefinitely());
    }
}
