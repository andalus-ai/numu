package tech.kayys.numou.courier.adapter.postgres;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CourierPodOutboxMigrationTest {
    @Test
    void migrationDefinesTenantScopedRetryableOutbox() throws IOException {
        Path migration = Path.of("src/main/resources/db/migration/V22__courier_pod_transactional_outbox.sql");
        String sql = Files.readString(migration, StandardCharsets.UTF_8).toLowerCase();

        assertTrue(sql.contains("primary key (tenant_id, id)"));
        assertTrue(sql.contains("payload jsonb not null"));
        assertTrue(sql.contains("published_at"));
        assertTrue(sql.contains("attempt_count"));
        assertTrue(sql.contains("lease_owner"));
        assertTrue(sql.contains("where published_at is null"));
        assertTrue(sql.contains("ck_courier_outbox_lease_pair"));
    }

    @Test
    void adapterQueuesOnlyOnNewEvidenceInsert() throws IOException {
        Path adapter = Path.of("src/main/java/tech/kayys/numou/courier/adapter/postgres/JdbcProofOfDeliveryStore.java");
        String source = Files.readString(adapter, StandardCharsets.UTF_8);

        assertTrue(source.contains("if (inserted == 1)"));
        assertTrue(source.contains("insertOutboxEvent(connection, command)"));
        assertTrue(source.contains("connection.commit()"));
        assertTrue(source.contains("Outcome.REPLAYED"));
        assertTrue(source.contains("Outcome.IDEMPOTENCY_CONFLICT"));
    }
}
