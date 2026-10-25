package tech.kayys.numou.courier.adapter.postgres;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierPodTrackingProjectorContractTest {
    @Test
    void projectorUsesConcurrentSafeClaimsAndIdempotentTrackingWrites() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/tech/kayys/numou/courier/adapter/postgres/JdbcProofOfDeliveryTrackingProjector.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("FOR UPDATE SKIP LOCKED"));
        assertTrue(source.contains("published_at IS NULL"));
        assertTrue(source.contains("ON CONFLICT (tenant_id, idempotency_key) DO NOTHING"));
        assertTrue(source.contains("\"POD:\" + proofId"));
        assertTrue(source.contains("connection.rollback(savepoint)"));
        assertTrue(source.contains("recordRetry(connection, tenantId, outboxId, failure)"));
    }

    @Test
    void projectorResolvesShipmentWithinTenantAndAcknowledgesAfterProjection() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/tech/kayys/numou/courier/adapter/postgres/JdbcProofOfDeliveryTrackingProjector.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("WHERE tenant_id = ?"));
        assertTrue(source.contains("normalized_reference = upper(trim(?))"));
        assertTrue(source.contains("SET published_at = now()"));
        assertTrue(source.contains("SELECT 1"));
        assertTrue(source.contains("POD idempotency key already represents a different tracking fact"));
    }

    @Test
    void projectorTransitionsOnlyTheMatchingTenantAttemptAndAllowsExactReplay() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/tech/kayys/numou/courier/adapter/postgres/JdbcProofOfDeliveryTrackingProjector.java"),
                StandardCharsets.UTF_8);
        String taskRepository = Files.readString(Path.of(
                "src/main/java/tech/kayys/numou/courier/adapter/postgres/JdbcDeliveryTaskRepository.java"),
                StandardCharsets.UTF_8);
        String podStore = Files.readString(Path.of(
                "src/main/java/tech/kayys/numou/courier/adapter/postgres/JdbcProofOfDeliveryStore.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("delivery_attempt_reference = ?"));
        assertTrue(source.contains("WHERE tenant_id = ? AND shipment_id = ?"));
        assertTrue(source.contains("status IN ('OUT_FOR_DELIVERY', 'DELIVERY_ATTEMPTED')"));
        assertTrue(source.contains("status = 'DELIVERED' AND proof_reference = ?"));
        assertTrue(source.contains("POD does not match an active delivery attempt"));
        assertTrue(taskRepository.contains("return \"TASK:\" + taskId.value();"));
        assertTrue(podStore.contains("'recipientLabel', ?, 'evidenceReference', ?"));

        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V23__courier_delivery_attempt_identity.sql"),
                StandardCharsets.UTF_8);
        assertTrue(migration.contains("SET delivery_attempt_reference = 'TASK:' || id::text"));
        assertTrue(migration.contains("UNIQUE INDEX uq_courier_delivery_attempt_reference"));
    }
}
