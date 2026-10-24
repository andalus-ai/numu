package tech.kayys.numou.courier.adapter.postgres;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CourierProofOfDeliveryMigrationTest {
    @Test
    void migrationDefinesTenantScopedImmutableEvidenceAndReplayKey() throws IOException {
        Path migration = Path.of("src/main/resources/db/migration/V21__courier_proof_of_delivery_evidence.sql");
        String sql = Files.readString(migration, StandardCharsets.UTF_8).toLowerCase();

        assertTrue(sql.contains("primary key (tenant_id, id)"));
        assertTrue(sql.contains("unique (tenant_id, source, idempotency_key)"));
        assertTrue(sql.contains("payload_fingerprint"));
        assertTrue(sql.contains("before update or delete"));
        assertTrue(sql.contains("courier_reject_pod_mutation"));
        assertTrue(sql.contains("evidence_sha256"));
    }
}
