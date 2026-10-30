package tech.kayys.numou.queuemanagement.postgres;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

/**
 * Boots the real PostgreSQL schema and a non-owner runtime role for Quarkus tests.
 * Runtime traffic deliberately does not connect as the migration/table owner.
 */
public final class QueuePostgresQuarkusTestResource implements QuarkusTestResourceLifecycleManager {
    private static final String RUNTIME_ROLE = "queue_runtime";
    private PostgreSQLContainer<?> postgres;

    @Override
    public Map<String, String> start() {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("queue_runtime_test")
                .withUsername("queue_owner")
                .withPassword("queue_owner");
        postgres.start();

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + RUNTIME_ROLE + " LOGIN PASSWORD 'queue_runtime' NOSUPERUSER NOBYPASSRLS");
            statement.execute("GRANT USAGE ON SCHEMA public TO " + RUNTIME_ROLE);
            statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO " + RUNTIME_ROLE);
        } catch (Exception e) {
            postgres.stop();
            throw new IllegalStateException("Unable to provision Queue Management PostgreSQL test role", e);
        }

        return Map.of(
                "quarkus.datasource.db-kind", "postgresql",
                "quarkus.datasource.jdbc.url", postgres.getJdbcUrl(),
                "quarkus.datasource.username", RUNTIME_ROLE,
                "quarkus.datasource.password", "queue_runtime"
        );
    }

    @Override
    public void stop() {
        if (postgres != null) postgres.stop();
    }
}
