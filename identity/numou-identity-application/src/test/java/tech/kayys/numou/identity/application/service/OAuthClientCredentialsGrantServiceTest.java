package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.identity.application.port.OAuthAccessTokenIssuerPort;
import tech.kayys.numou.identity.application.port.OAuthClientCredentialRepository;
import tech.kayys.numou.identity.application.security.ClientSecretGenerator;
import tech.kayys.numou.identity.domain.clientcredential.OAuthClientCredential;
import tech.kayys.numou.identity.domain.clientcredential.OAuthClientCredentialId;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OAuthClientCredentialsGrantServiceTest {
    private final ClientSecretGenerator secrets = new ClientSecretGenerator();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-04T00:00:00Z");

    @Test
    void authenticatesClientAndIssuesOnlyRequestedGrantedScopes() {
        var material = secrets.generate();
        String rawSecret = material.takeSecret();
        var credential = OAuthClientCredential.issue(OAuthClientCredentialId.newId(), tenantId,
                applicationId, "nmc_test_" + UUID.randomUUID().toString().replace("-", ""),
                material.secretHash(), material.displayPrefix(), Set.of("orders.read", "orders.write"),
                now.plusSeconds(3600), now.minusSeconds(60));
        var issued = new AtomicReference<String>();
        var capturedTenant = new AtomicReference<UUID>();
        var capturedApp = new AtomicReference<UUID>();
        var capturedScopes = new AtomicReference<Set<String>>();
        OAuthAccessTokenIssuerPort issuer = (client, tenant, app, scopes, version) -> {
            issued.set(client);
            capturedTenant.set(tenant);
            capturedApp.set(app);
            capturedScopes.set(scopes);
            return "signed-access-token";
        };
        var service = service(credential, issuer);

        var response = service.exchange(credential.clientId(), rawSecret, "orders.read")
                .await().indefinitely();

        assertEquals("signed-access-token", response.access_token());
        assertEquals("Bearer", response.token_type());
        assertEquals(300, response.expires_in());
        assertEquals("orders.read", response.scope());
        assertEquals(credential.clientId(), issued.get());
        assertEquals(tenantId, capturedTenant.get());
        assertEquals(applicationId, capturedApp.get());
        assertEquals(Set.of("orders.read"), capturedScopes.get());
    }

    @Test
    void rejectsWrongSecretAndUnknownClientWithSamePublicError() {
        var material = secrets.generate();
        var credential = credential(material.secretHash(), material.displayPrefix());
        var service = service(credential, (c, t, a, s, v) -> "unused");

        assertEquals("invalid_client", assertThrows(OAuthGrantException.class,
                () -> service.exchange(credential.clientId(), "wrong-secret", "orders.read").await().indefinitely()).error());
        assertEquals("invalid_client", assertThrows(OAuthGrantException.class,
                () -> service.exchange("unknown-client", "wrong-secret", "orders.read").await().indefinitely()).error());
    }

    @Test
    void rejectsScopeEscalationAndMalformedScope() {
        var material = secrets.generate();
        var credential = credential(material.secretHash(), material.displayPrefix());
        var service = service(credential, (c, t, a, s, v) -> "unused");
        String secret = material.takeSecret();

        assertEquals("invalid_scope", assertThrows(OAuthGrantException.class,
                () -> service.exchange(credential.clientId(), secret, "orders.read admin").await().indefinitely()).error());
        assertEquals("invalid_scope", assertThrows(OAuthGrantException.class,
                () -> service.exchange(credential.clientId(), secret, "Orders.Read").await().indefinitely()).error());
    }

    @Test
    void rejectsRevokedAndExpiredCredentials() {
        var material = secrets.generate();
        var revoked = credential(material.secretHash(), material.displayPrefix());
        revoked.revoke(now.minusSeconds(1));
        var service = service(revoked, (c, t, a, s, v) -> "unused");
        String secret = material.takeSecret();
        assertEquals("invalid_client", assertThrows(OAuthGrantException.class,
                () -> service.exchange(revoked.clientId(), secret, "orders.read").await().indefinitely()).error());

        var expiredMaterial = secrets.generate();
        var expired = OAuthClientCredential.issue(OAuthClientCredentialId.newId(), tenantId, applicationId,
                "nmc_expired_" + UUID.randomUUID().toString().replace("-", ""),
                expiredMaterial.secretHash(), expiredMaterial.displayPrefix(), Set.of("orders.read"),
                now.minusSeconds(1), now.minusSeconds(60));
        var expiredService = service(expired, (c, t, a, s, v) -> "unused");
        assertEquals("invalid_client", assertThrows(OAuthGrantException.class,
                () -> expiredService.exchange(expired.clientId(), expiredMaterial.takeSecret(), "orders.read")
                        .await().indefinitely()).error());
    }

    private OAuthClientCredential credential(String hash, String prefix) {
        return OAuthClientCredential.issue(OAuthClientCredentialId.newId(), tenantId, applicationId,
                "nmc_test_" + UUID.randomUUID().toString().replace("-", ""), hash, prefix,
                Set.of("orders.read", "orders.write"), now.plusSeconds(3600), now.minusSeconds(60));
    }

    private OAuthClientCredentialsGrantService service(OAuthClientCredential credential, OAuthAccessTokenIssuerPort issuer) {
        OAuthClientCredentialRepository repository = new OAuthClientCredentialRepository() {
            public Uni<Optional<OAuthClientCredential>> findById(UUID tenant, OAuthClientCredentialId id) {
                return Uni.createFrom().item(tenant.equals(credential.tenantId()) && id.equals(credential.id())
                        ? Optional.of(credential) : Optional.empty());
            }
            public Uni<Optional<OAuthClientCredential>> findByClientId(UUID tenant, String clientId) {
                return Uni.createFrom().item(tenant.equals(credential.tenantId()) && clientId.equals(credential.clientId())
                        ? Optional.of(credential) : Optional.empty());
            }
            public Uni<Optional<OAuthClientCredential>> findByClientId(String clientId) {
                return Uni.createFrom().item(clientId.equals(credential.clientId()) ? Optional.of(credential) : Optional.empty());
            }
            public Uni<Void> save(OAuthClientCredential value) { return Uni.createFrom().voidItem(); }
        };
        return new OAuthClientCredentialsGrantService(repository, secrets, issuer,
                Clock.fixed(now, ZoneOffset.UTC), 300);
    }
}
