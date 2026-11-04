package tech.kayys.numou.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.identity.application.port.OAuthClientCredentialRepository;
import tech.kayys.numou.identity.domain.clientcredential.OAuthClientCredential;
import tech.kayys.numou.identity.domain.clientcredential.OAuthClientCredentialId;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(IdentityPostgresTestResource.class)
class PostgresOAuthClientCredentialRepositoryTest {
    @Inject OAuthClientCredentialRepository repository;

    private UUID tenantId;
    private UUID otherTenantId;
    private UUID applicationId;
    private OAuthClientCredential credential;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        otherTenantId = UUID.randomUUID();
        applicationId = UUID.randomUUID();
        Panache.withTransaction(() -> Panache.getSession()
                .chain(session -> session.createNativeQuery(
                        "insert into tenants (id, name, status, version) values (:id, 'oauth-test', 'ACTIVE', 0)")
                        .setParameter("id", tenantId).executeUpdate())
                .chain(() -> Panache.getSession().chain(session -> session.createNativeQuery(
                        "insert into tenants (id, name, status, version) values (:id, 'oauth-other', 'ACTIVE', 0)")
                        .setParameter("id", otherTenantId).executeUpdate())))
                .await().indefinitely();

        credential = OAuthClientCredential.issue(OAuthClientCredentialId.newId(), tenantId,
                applicationId, "nmc_test_" + UUID.randomUUID().toString().replace("-", ""),
                "a".repeat(64), "nmc_live_ab", Set.of("orders.read", "orders.write"),
                Instant.now().plusSeconds(3600), Instant.now());
    }

    @Test
    void savesAndLoadsOnlyWithinTheOwningTenant() {
        Panache.withTransaction(() -> repository.save(credential)).await().indefinitely();

        var found = repository.findById(tenantId, credential.id()).await().indefinitely();
        assertTrue(found.isPresent());
        assertEquals(tenantId, found.orElseThrow().tenantId());
        assertEquals(applicationId, found.orElseThrow().applicationId());
        assertEquals(credential.scopes(), found.orElseThrow().scopes());

        assertTrue(repository.findById(otherTenantId, credential.id()).await().indefinitely().isEmpty());
        assertTrue(repository.findByClientId(otherTenantId, credential.clientId()).await().indefinitely().isEmpty());
    }

    @Test
    void persistsRotationWithoutCreatingASecondCredential() {
        Panache.withTransaction(() -> repository.save(credential)).await().indefinitely();
        credential.rotate("b".repeat(64), "nmc_live_cd", Instant.now().plusSeconds(7200), Instant.now());
        Panache.withTransaction(() -> repository.save(credential)).await().indefinitely();

        var loaded = repository.findByClientId(tenantId, credential.clientId()).await().indefinitely().orElseThrow();
        assertEquals(2, loaded.credentialVersion());
        assertEquals("b".repeat(64), loaded.secretHash());
    }
}
