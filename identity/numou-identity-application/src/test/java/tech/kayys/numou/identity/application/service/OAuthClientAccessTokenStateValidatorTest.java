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
    private final UUID application = UUID.randomUUID();

    @Test
    void acceptsCurrentActiveCredentialAndReturnsAuthoritativeIdentity() {
        OAuthClientCredential credential = credential();
        var result = validator(credential).validate("access", credential.clientId(), tenant.toString(),
                application.toString(), 1, Set.of("orders.read")).await().indefinitely();
        assertEquals(tenant, result.tenantId());
        assertEquals(application, result.applicationId());
        assertEquals(Set.of("orders.read"), result.scopes());
    }

    @Test
    void rejectsRevokedExpiredStaleMismatchedAndEscalatedTokens() {
        OAuthClientCredential credential = credential();
        var validator = validator(credential);
        rejected(validator.validate("refresh", credential.clientId(), tenant.toString(), application.toString(), 1, Set.of("orders.read")));
        rejected(validator.validate("access", credential.clientId(), UUID.randomUUID().toString(), application.toString(), 1, Set.of("orders.read")));
        rejected(validator.validate("access", credential.clientId(), tenant.toString(), application.toString(), 1, Set.of("admin")));
        rejected(validator.validate("access", credential.clientId(), tenant.toString(), application.toString(), 2, Set.of("orders.read")));
        credential.revoke(NOW);
        rejected(validator.validate("access", credential.clientId(), tenant.toString(), application.toString(), 1, Set.of("orders.read")));
    }

    @Test
    void rejectsMalformedAndUnboundedClaims() {
        OAuthClientCredential credential = credential();
        var validator = validator(credential);
        rejected(validator.validate("access", credential.clientId(), "bad", application.toString(), 1, Set.of("orders.read")));
        rejected(validator.validate("access", credential.clientId(), tenant.toString(), application.toString(), 1, Set.of("Bad.Scope")));
        rejected(validator.validate("access", credential.clientId(), tenant.toString(), application.toString(), 1, Set.of()));
    }

    private OAuthClientCredential credential() {
        return OAuthClientCredential.reconstitute(OAuthClientCredentialId.newId(), tenant, application,
                "nmc_test_" + UUID.randomUUID().toString().replace("-", ""), "a".repeat(64), "prefix",
                Set.of("orders.read"), OAuthClientCredentialStatus.ACTIVE, 1,
                NOW.minusSeconds(60), NOW.plusSeconds(3600), null, null);
    }

    private OAuthClientAccessTokenStateValidator validator(OAuthClientCredential credential) {
        OAuthClientCredentialRepository repository = new OAuthClientCredentialRepository() {
            public Uni<Optional<OAuthClientCredential>> findById(UUID tenantId, OAuthClientCredentialId id) {
                return Uni.createFrom().item(Optional.empty());
            }
            public Uni<Optional<OAuthClientCredential>> findByClientId(UUID tenantId, String id) {
                return Uni.createFrom().item(Optional.empty());
            }
            public Uni<Optional<OAuthClientCredential>> findByClientId(String id) {
                return Uni.createFrom().item(id.equals(credential.clientId()) ? Optional.of(credential) : Optional.empty());
            }
            public Uni<Void> save(OAuthClientCredential value) { return Uni.createFrom().voidItem(); }
        };
        return new OAuthClientAccessTokenStateValidator(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static void rejected(Uni<?> result) {
        assertThrows(OAuthTokenRejectedException.class, () -> result.await().indefinitely());
    }
}
