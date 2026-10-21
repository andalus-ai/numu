package tech.kayys.numou.identity.adapter.outbound.postgres;

import java.util.Map;
import org.testcontainers.containers.PostgreSQLContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/** Supplies an isolated PostgreSQL instance for identity persistence contract tests. */
public class IdentityPostgresTestResource implements QuarkusTestResourceLifecycleManager {
    private PostgreSQLContainer<?> postgres;

    @Override
    public Map<String, String> start() {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("numou_identity_test")
                .withUsername("numou_test")
                .withPassword("numou_test");
        postgres.start();
        return Map.of(
                "quarkus.datasource.db-kind", "postgresql",
                "quarkus.datasource.username", postgres.getUsername(),
                "quarkus.datasource.password", postgres.getPassword(),
                "quarkus.datasource.reactive.url", "vertx-reactive:postgresql://"
                        + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/"
                        + postgres.getDatabaseName(),
                "quarkus.datasource.jdbc.url", postgres.getJdbcUrl(),
                "quarkus.flyway.migrate-at-start", "true",
                "quarkus.hibernate-orm.database.generation", "validate",
                "quarkus.kafka.devservices.enabled", "false",
                "mp.messaging.outgoing.identity-events.connector", "smallrye-kafka",
                "mp.messaging.outgoing.identity-audit-log.connector", "smallrye-kafka");
    }

    @Override
    public void stop() {
        if (postgres != null) {
            postgres.stop();
        }
    }
}
