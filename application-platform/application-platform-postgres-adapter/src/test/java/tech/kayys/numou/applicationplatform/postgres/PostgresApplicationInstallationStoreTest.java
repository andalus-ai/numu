package tech.kayys.numou.applicationplatform.postgres;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.postgresql.ds.PGSimpleDataSource;
import tech.kayys.numou.applicationplatform.application.installation.ApplicationInstallationOutboxStore;
import tech.kayys.numou.applicationplatform.application.installation.ApplicationInstallationStore;
import tech.kayys.numou.applicationplatform.domain.installation.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresApplicationInstallationStoreTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static DataSource adminDataSource;
    static DataSource dataSource;
    static PostgresApplicationInstallationStore store;

    @BeforeAll
    static void migrate() throws Exception {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        adminDataSource = source;
        try (var connection = source.getConnection(); var statement = connection.createStatement();
             InputStream migration = PostgresApplicationInstallationStoreTest.class
                     .getResourceAsStream("/db/migration/V1__application_installation.sql")) {
            assertThat(migration).as("installation migration resource").isNotNull();
            statement.execute(new String(migration.readAllBytes(), StandardCharsets.UTF_8));
            statement.execute("CREATE ROLE numou_app_test LOGIN PASSWORD 'numou-app-test' NOSUPERUSER NOBYPASSRLS");
            statement.execute("GRANT USAGE ON SCHEMA public TO numou_app_test");
            statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO numou_app_test");
        }
        PGSimpleDataSource applicationRole = new PGSimpleDataSource();
        applicationRole.setURL(POSTGRES.getJdbcUrl());
        applicationRole.setUser("numou_app_test");
        applicationRole.setPassword("numou-app-test");
        dataSource = applicationRole;
        store = new PostgresApplicationInstallationStore(dataSource);
    }

    @Test
    void createIsAtomicAndSameIdempotencyRequestReplays() {
        TenantId tenant = TenantId.generate();
        Instant now = Instant.parse("2026-11-03T10:00:00Z");
        ApplicationInstallation requested = requested(tenant, "com.example.retail", "1.0.0", "actor-a", now);
        String keyHash = hash("client-key-00000001");

        var created = store.createIfAbsent(requested, keyHash, requested.requestedEvent("corr-1", now))
                .toCompletableFuture().join();
        assertThat(created.outcome()).isEqualTo(ApplicationInstallationStore.CreateResult.Outcome.CREATED);

        ApplicationInstallation duplicate = requested(tenant, "com.example.retail", "1.0.0", "actor-a", now.plusSeconds(1));
        var replay = store.createIfAbsent(duplicate, keyHash, duplicate.requestedEvent("corr-2", now.plusSeconds(1)))
                .toCompletableFuture().join();
        assertThat(replay.outcome()).isEqualTo(ApplicationInstallationStore.CreateResult.Outcome.REPLAYED);
        assertThat(replay.installation().orElseThrow().id()).isEqualTo(created.installation().orElseThrow().id());
        assertThat(count("application_installations", tenant)).isEqualTo(1);
        assertThat(count("application_installation_transitions", tenant)).isEqualTo(1);
        assertThat(count("application_installation_outbox", tenant)).isEqualTo(1);
    }

    @Test
    void rejectsConflictingIdempotencyReuseAndDuplicateTenantApplication() {
        TenantId tenant = TenantId.generate();
        Instant now = Instant.parse("2026-11-03T10:01:00Z");
        String key = hash("client-key-00000002");
        var first = requested(tenant, "com.example.crm", "1.0.0", "actor-a", now);
        assertThat(store.createIfAbsent(first, key, first.requestedEvent(now)).toCompletableFuture().join().outcome())
                .isEqualTo(ApplicationInstallationStore.CreateResult.Outcome.CREATED);

        var changedRequest = requested(tenant, "com.example.crm", "2.0.0", "actor-a", now.plusSeconds(1));
        assertThat(store.createIfAbsent(changedRequest, key, changedRequest.requestedEvent(now.plusSeconds(1)))
                .toCompletableFuture().join().outcome()).isEqualTo(ApplicationInstallationStore.CreateResult.Outcome.CONFLICT);

        var anotherKey = requested(tenant, "com.example.crm", "1.0.0", "actor-a", now.plusSeconds(2));
        assertThat(store.createIfAbsent(anotherKey, hash("client-key-00000003"),
                anotherKey.requestedEvent(now.plusSeconds(2))).toCompletableFuture().join().outcome())
                .isEqualTo(ApplicationInstallationStore.CreateResult.Outcome.CONFLICT);
        assertThat(count("application_installations", tenant)).isEqualTo(1);
    }

    @Test
    void tenantRlsAndCompositeKeysPreventCrossTenantReads() {
        TenantId tenantA = TenantId.generate();
        TenantId tenantB = TenantId.generate();
        Instant now = Instant.parse("2026-11-03T10:02:00Z");
        var installation = requested(tenantA, "com.example.finance", "1.0.0", "actor-a", now);
        store.createIfAbsent(installation, hash("client-key-00000004"), installation.requestedEvent(now))
                .toCompletableFuture().join();

        assertThat(store.find(tenantA, installation.id()).toCompletableFuture().join()).isPresent();
        assertThat(store.find(tenantB, installation.id()).toCompletableFuture().join()).isEmpty();
        assertThat(count("application_installations", tenantB)).isZero();
        assertThat(count("application_installations", null)).isZero();
    }

    @Test
    void transitionUsesRevisionCasAndWritesHistoryAndOutboxTogether() {
        TenantId tenant = TenantId.generate();
        Instant now = Instant.parse("2026-11-03T10:03:00Z");
        var installation = requested(tenant, "com.example.inventory", "1.0.0", "actor-a", now);
        store.createIfAbsent(installation, hash("client-key-00000005"), installation.requestedEvent(now))
                .toCompletableFuture().join();

        var loaded = store.find(tenant, installation.id()).toCompletableFuture().join().orElseThrow();
        var transition = loaded.beginValidation("operator-1", "corr-validate", now.plusSeconds(1));
        assertThat(store.appendTransition(loaded, transition, 1).toCompletableFuture().join()).isTrue();
        assertThat(store.appendTransition(loaded, transition, 1).toCompletableFuture().join()).isFalse();

        var persisted = store.find(tenant, installation.id()).toCompletableFuture().join().orElseThrow();
        assertThat(persisted.status()).isEqualTo(ApplicationInstallationStatus.VALIDATING);
        assertThat(persisted.revision()).isEqualTo(2);
        assertThat(count("application_installation_transitions", tenant)).isEqualTo(2);
        assertThat(count("application_installation_outbox", tenant)).isEqualTo(2);
    }

    @Test
    void outboxClaimsAreTenantScopedLeaseFencedAndDeadLetterAfterBoundedAttempts() {
        TenantId tenant = TenantId.generate();
        TenantId otherTenant = TenantId.generate();
        Instant now = Instant.parse("2026-11-03T10:04:00Z");
        var installation = requested(tenant, "com.example.support", "1.0.0", "actor-a", now);
        store.createIfAbsent(installation, hash("client-key-00000006"), installation.requestedEvent(now))
                .toCompletableFuture().join();
        var second = requested(tenant, "com.example.supportdesk", "1.0.0", "actor-a", now.plusSeconds(1));
        store.createIfAbsent(second, hash("client-key-00000008"), second.requestedEvent(now.plusSeconds(1)))
                .toCompletableFuture().join();

        var claimed = store.claimDue(tenant, "worker-a", now.plusSeconds(2), Duration.ofSeconds(30), 10)
                .toCompletableFuture().join();
        assertThat(claimed).hasSize(2);
        assertThat(store.claimDue(otherTenant, "worker-b", now.plusSeconds(2), Duration.ofSeconds(30), 10)
                .toCompletableFuture().join()).isEmpty();
        assertThat(store.claimDue(tenant, "worker-b", now.plusSeconds(3), Duration.ofSeconds(30), 10)
                .toCompletableFuture().join()).isEmpty();

        var recovered = store.claimDue(tenant, "worker-b", now.plusSeconds(40), Duration.ofSeconds(30), 10)
                .toCompletableFuture().join();
        assertThat(recovered).hasSize(2);
        var dead = recovered.get(0);
        var delivered = recovered.get(1);
        assertThat(dead.attempts()).isEqualTo(2);
        assertThat(store.markDelivered(tenant, dead.eventId(), "worker-a", claimed.get(0).leaseToken(), now.plusSeconds(41))
                .toCompletableFuture().join()).isFalse();
        assertThat(store.markDelivered(tenant, delivered.eventId(), "worker-b", delivered.leaseToken(), now.plusSeconds(41))
                .toCompletableFuture().join()).isTrue();
        assertThat(store.retry(tenant, dead.eventId(), "worker-b", dead.leaseToken(),
                "PROVISIONING_TEMPORARY_FAILURE", now.plusSeconds(60), now.plusSeconds(41), 2)
                .toCompletableFuture().join()).isEqualTo(ApplicationInstallationOutboxStore.RetryOutcome.DEAD_LETTERED);

        // The DEAD delivery and FAILED aggregate transition must commit atomically.
        var failedInstallation = store.find(tenant, new ApplicationInstallationId(dead.installationId()))
                .toCompletableFuture().join().orElseThrow();
        assertThat(failedInstallation.status()).isEqualTo(ApplicationInstallationStatus.FAILED);
        assertThat(failedInstallation.failureCode()).contains("PROVISIONING_TEMPORARY_FAILURE");
        assertThat(failedInstallation.revision()).isEqualTo(2);
        assertThat(count("application_installation_transitions", tenant)).isEqualTo(3);
        assertThat(count("application_installation_outbox", tenant)).isEqualTo(3);
        assertThat(store.claimDue(tenant, "worker-c", now.plusSeconds(120), Duration.ofSeconds(30), 10)
                .toCompletableFuture().join()).isEmpty();
    }

    @Test
    void transitionHistoryRejectsMutation() throws Exception {
        TenantId tenant = TenantId.generate();
        Instant now = Instant.parse("2026-11-03T10:05:00Z");
        var installation = requested(tenant, "com.example.audit", "1.0.0", "actor-a", now);
        store.createIfAbsent(installation, hash("client-key-00000007"), installation.requestedEvent(now))
                .toCompletableFuture().join();
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("SELECT set_config('numou.tenant_id','" + tenant.value() + "',false)");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE application_installation_transitions SET reason_code='tampered' WHERE tenant_id='" + tenant.value() + "'"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate(
                    "DELETE FROM application_installation_transitions WHERE tenant_id='" + tenant.value() + "'"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test
    void idempotencyAndTransitionLedgersRejectMutationAndTruncation() {
        TenantId tenant = TenantId.generate();
        Instant now = Instant.parse("2026-11-03T10:06:00Z");
        var installation = requested(tenant, "com.example.immutable", "1.0.0", "actor-a", now);
        String keyHash = hash("client-key-00000009");
        store.createIfAbsent(installation, keyHash, installation.requestedEvent(now))
                .toCompletableFuture().join();
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("SELECT set_config('numou.tenant_id','" + tenant.value() + "',false)");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "DELETE FROM application_installation_idempotency WHERE tenant_id='" + tenant.value() + "'"))
                    .isInstanceOf(java.sql.SQLException.class);
        } catch (java.sql.SQLException failure) {
            throw new IllegalStateException(failure);
        }
        // Exercise the database trigger itself as the table owner; the runtime role
        // deliberately has no TRUNCATE privilege.
        try (var connection = adminDataSource.getConnection(); var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.execute("TRUNCATE application_installation_transitions"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("TRUNCATE application_installation_idempotency"))
                    .isInstanceOf(java.sql.SQLException.class);
        } catch (java.sql.SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static ApplicationInstallation requested(TenantId tenant, String app, String version,
            String actor, Instant now) {
        return ApplicationInstallation.request(tenant, new ApplicationId(app), new PackageVersion(version),
                "a".repeat(64), actor, now);
    }

    private long count(String table, TenantId tenant) {
        if (!java.util.Set.of("application_installations", "application_installation_transitions",
                "application_installation_outbox").contains(table)) throw new IllegalArgumentException("table");
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var set = connection.prepareStatement("SELECT set_config('numou.tenant_id', ?, true)")) {
                set.setString(1, tenant == null ? "" : tenant.value().toString());
                set.execute();
            }
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT count(*) FROM " + table)) {
                result.next();
                long count = result.getLong(1);
                connection.commit();
                return count;
            }
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
