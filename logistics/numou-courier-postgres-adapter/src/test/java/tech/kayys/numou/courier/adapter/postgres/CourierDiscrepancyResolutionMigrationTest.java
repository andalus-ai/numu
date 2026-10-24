package tech.kayys.numou.courier.adapter.postgres;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CourierDiscrepancyResolutionMigrationTest {
    @Test
    void migrationAddsAppendOnlyTenantScopedResolutionEvidence() throws IOException {
        String migration = migration();

        assertTrue(migration.contains("CREATE TABLE courier_dispatch_wave_discrepancy_resolutions"));
        assertTrue(migration.contains("FOREIGN KEY (tenant_id, wave_id)"));
        assertTrue(migration.contains("FOREIGN KEY (tenant_id, discrepancy_id)"));
        assertTrue(migration.contains("UNIQUE (tenant_id, source, idempotency_key)"));
        assertTrue(migration.contains("permission_reference VARCHAR(200) NOT NULL"));
        assertTrue(migration.contains("payload_fingerprint VARCHAR(128) NOT NULL"));
        assertTrue(migration.contains("trg_courier_wave_discrepancy_resolution_immutable"));
        assertTrue(migration.contains("BEFORE UPDATE OR DELETE ON courier_dispatch_wave_discrepancy_resolutions"));
    }

    @Test
    void departureGateRequiresResolutionEvidenceAndNeverTrustsFreeFormExceptionText() throws IOException {
        String migration = migration();

        assertTrue(migration.contains("CREATE OR REPLACE FUNCTION courier_guard_dispatch_wave_departure"));
        assertTrue(migration.contains("r.resolution_code IN ('WAIVE_MISSING','REMOVE_FROM_MANIFEST')"));
        assertTrue(migration.contains("r.discrepancy_id = d.id"));
        assertTrue(migration.contains("NOT EXISTS ("));
        assertFalse(migration.contains("NEW.authorized_exception_reason"));
        assertFalse(migration.contains("authorized_exception_reason IS NOT NULL"));
    }

    @Test
    void resolutionMustMatchWaveParcelAndCompatibleDiscrepancyType() throws IOException {
        String migration = migration();

        assertTrue(migration.contains("FOR UPDATE"));
        assertTrue(migration.contains("discrepancy_wave <> NEW.wave_id"));
        assertTrue(migration.contains("discrepancy_parcel <> NEW.parcel_reference"));
        assertTrue(migration.contains("resolution code is incompatible with discrepancy type"));
        assertTrue(migration.contains("wave_status <> 'LOADING'"));
    }

    private String migration() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/V20__courier_load_discrepancy_resolution.sql")) {
            assertNotNull(input, "V20 migration must be on the classpath");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}