package tech.kayys.numou.applicationplatform.postgres;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.postgresql.ds.PGSimpleDataSource;
import tech.kayys.numou.applicationplatform.domain.configuration.ApplicationConfigurationSnapshot;
import tech.kayys.numou.applicationplatform.domain.installation.ApplicationId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresApplicationConfigurationStoreTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static DataSource admin;
    static DataSource applicationRole;
    static PostgresApplicationConfigurationStore store;

    @BeforeAll
    static void migrate() throws Exception {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        admin = source;
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            for (String migrationName : new String[] {
                    "/db/migration/V1__application_installation.sql",
                    "/db/migration/V2__tenant_application_configuration.sql" }) {
                try (InputStream migration = PostgresApplicationConfigurationStoreTest.class
                        .getResourceAsStream(migrationName)) {
                    assertThat(migration).as(migrationName).isNotNull();
                    statement.execute(new String(migration.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            statement.execute("CREATE ROLE numou_config_test LOGIN PASSWORD 'numou-config-test' NOSUPERUSER NOBYPASSRLS");
            statement.execute("GRANT USAGE ON SCHEMA public TO numou_config_test");
            statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO numou_config_test");
        }
        PGSimpleDataSource app = new PGSimpleDataSource();
        app.setURL(POSTGRES.getJdbcUrl());
        app.setUser("numou_config_test");
        app.setPassword("numou-config-test");
        applicationRole = app;
        store = new PostgresApplicationConfigurationStore(applicationRole);
    }

    @Test
    void createUpdateAndStaleRevisionAreAtomicAndHistoryIsAppendOnly() throws Exception {
        TenantId tenant = TenantId.generate();
        ApplicationId app = new ApplicationId("com.example.crm");
        createInstallation(tenant, app);
        Instant now = Instant.parse("2026-11-04T05:00:00Z");

        var first = ApplicationConfigurationSnapshot.first(tenant, app, "1.0",
                "{\"locale\":\"id-ID\"}", "user:a", now);
        var created = store.save(first, 0).toCompletableFuture().join();
        assertThat(created.saved()).isTrue();
        assertThat(created.current()).contains(first);

        var next = first.next("1.1", "{\"locale\":\"en-US\"}", "user:b", now.plusSeconds(1));
        assertThat(store.save(next, 1).toCompletableFuture().join().saved()).isTrue();

        var stale = first.next("1.1", "{\"locale\":\"ms-MY\"}", "user:c", now.plusSeconds(2));
        var rejected = store.save(stale, 1).toCompletableFuture().join();
        assertThat(rejected.saved()).isFalse();
        assertThat(rejected.current()).contains(next);
        assertThat(store.findCurrent(tenant, app.value()).toCompletableFuture().join()).contains(next);
        assertThat(count("application_configuration_revisions", tenant)).isEqualTo(2);

        try (var connection = applicationRole.getConnection(); var statement = connection.createStatement()) {
            statement.execute("SELECT set_config('numou.tenant_id','" + tenant.value() + "',false)");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE application_configuration_revisions SET updated_by='tampered' WHERE revision=1"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test
    void concurrentWritersWithSameExpectedRevisionHaveExactlyOneWinner() throws Exception {
        TenantId tenant = TenantId.generate();
        ApplicationId app = new ApplicationId("com.example.sales");
        createInstallation(tenant, app);
        Instant now = Instant.parse("2026-11-04T05:10:00Z");
        var first = ApplicationConfigurationSnapshot.first(tenant, app, "1.0", "{\"region\":\"ID\"}", "user:a", now);
        assertThat(store.save(first, 0).toCompletableFuture().join().saved()).isTrue();

        var writerA = first.next("1.0", "{\"region\":\"ID-JK\"}", "user:a", now.plusSeconds(1));
        var writerB = first.next("1.0", "{\"region\":\"ID-BD\"}", "user:b", now.plusSeconds(1));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> {
                ready.countDown(); start.await();
                return store.save(writerA, 1).toCompletableFuture().join().saved();
            });
            var b = executor.submit(() -> {
                ready.countDown(); start.await();
                return store.save(writerB, 1).toCompletableFuture().join().saved();
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(store.findCurrent(tenant, app.value()).toCompletableFuture().join()).get()
                .extracting(ApplicationConfigurationSnapshot::revision).isEqualTo(2L);
        assertThat(count("application_configuration_revisions", tenant)).isEqualTo(2);
    }

    @Test
    void tenantRlsHidesOtherTenantsConfiguration() throws Exception {
        TenantId tenantA = TenantId.generate();
        TenantId tenantB = TenantId.generate();
        ApplicationId app = new ApplicationId("com.example.inventory");
        createInstallation(tenantA, app);
        createInstallation(tenantB, app);
        var snapshot = ApplicationConfigurationSnapshot.first(tenantA, app, "1.0",
                "{\"warehouse\":\"A\"}", "user:a", Instant.parse("2026-11-04T05:20:00Z"));
        assertThat(store.save(snapshot, 0).toCompletableFuture().join().saved()).isTrue();
        assertThat(store.findCurrent(tenantB, app.value()).toCompletableFuture().join()).isEmpty();
        assertThat(count("application_configurations", tenantB)).isZero();
    }

    @Test
    void historyUsesStableDescendingExclusiveCursorAndEnforcesPageBound() throws Exception {
        TenantId tenant = TenantId.generate();
        ApplicationId app = new ApplicationId("com.example.history");
        createInstallation(tenant, app);
        Instant now = Instant.parse("2026-11-04T05:30:00Z");
        var current = ApplicationConfigurationSnapshot.first(
                tenant, app, "1.0", "{\"revision\":1}", "user:a", now);
        assertThat(store.save(current, 0).toCompletableFuture().join().saved()).isTrue();
        for (int revision = 2; revision <= 5; revision++) {
            var next = current.next("1.0", "{\"revision\":" + revision + "}",
                    "user:a", now.plusSeconds(revision));
            assertThat(store.save(next, current.revision()).toCompletableFuture().join().saved()).isTrue();
            current = next;
        }

        var firstPage = store.findHistory(tenant, app, null, 2).toCompletableFuture().join();
        assertThat(firstPage.revisions()).extracting(ApplicationConfigurationSnapshot::revision)
                .containsExactly(5L, 4L);
        assertThat(firstPage.nextBeforeRevision()).isEqualTo(4L);

        var secondPage = store.findHistory(tenant, app, firstPage.nextBeforeRevision(), 2)
                .toCompletableFuture().join();
        assertThat(secondPage.revisions()).extracting(ApplicationConfigurationSnapshot::revision)
                .containsExactly(3L, 2L);
        assertThat(secondPage.nextBeforeRevision()).isEqualTo(2L);

        var finalPage = store.findHistory(tenant, app, secondPage.nextBeforeRevision(), 2)
                .toCompletableFuture().join();
        assertThat(finalPage.revisions()).extracting(ApplicationConfigurationSnapshot::revision)
                .containsExactly(1L);
        assertThat(finalPage.nextBeforeRevision()).isNull();

        assertThatThrownBy(() -> store.findHistory(tenant, app, null, 101).toCompletableFuture().join())
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    private static void createInstallation(TenantId tenant, ApplicationId app) throws Exception {
        try (var connection = applicationRole.getConnection()) {
            connection.setAutoCommit(false);
            try (var set = connection.prepareStatement("SELECT set_config('numou.tenant_id', ?, true)")) {
                set.setString(1, tenant.value().toString());
                set.execute();
            }
            String sql = """
                    INSERT INTO application_installations
                    (id,tenant_id,application_id,package_version,package_digest,status,revision,
                     created_at,updated_at,created_by,updated_by)
                    VALUES (?,?,?,'1.0.0',?,'INSTALLED',1,now(),now(),'test','test')
                    """;
            try (var insert = connection.prepareStatement(sql)) {
                insert.setObject(1, UUID.randomUUID());
                insert.setObject(2, tenant.value());
                insert.setString(3, app.value());
                insert.setString(4, "a".repeat(64));
                insert.executeUpdate();
            }
            connection.commit();
        }
    }

    private static int count(String table, TenantId tenant) throws Exception {
        try (var connection = applicationRole.getConnection()) {
            connection.setAutoCommit(false);
            try (var set = connection.prepareStatement("SELECT set_config('numou.tenant_id', ?, true)")) {
                set.setString(1, tenant.value().toString());
                set.execute();
            }
            try (var statement = connection.prepareStatement("SELECT count(*) FROM " + table);
                 var result = statement.executeQuery()) {
                result.next();
                int count = result.getInt(1);
                connection.commit();
                return count;
            }
        }
    }
}
