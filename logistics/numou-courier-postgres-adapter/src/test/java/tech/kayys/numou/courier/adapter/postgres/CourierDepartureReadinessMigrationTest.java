package tech.kayys.numou.courier.adapter.postgres;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CourierDepartureReadinessMigrationTest {
    @Test
    void departureGuardFailsClosedForMissingAndDiscrepantParcels() throws IOException {
        String migration;
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/V19__courier_dispatch_wave_departure_readiness.sql")) {
            assertNotNull(input, "V19 migration must be on the classpath");
            migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(migration.contains("CREATE OR REPLACE FUNCTION courier_guard_dispatch_wave_departure"));
        assertTrue(migration.contains("NEW.status = 'DEPARTED'"));
        assertTrue(migration.contains("p.tenant_id = NEW.tenant_id"));
        assertTrue(migration.contains("p.wave_id = NEW.id"));
        assertTrue(migration.contains("p.expected = TRUE AND p.scan_count = 0"));
        assertTrue(migration.contains("p.discrepancy IS NOT NULL"));
        assertTrue(migration.contains("FROM courier_dispatch_wave_discrepancies d"));
        assertTrue(migration.contains("d.tenant_id = NEW.tenant_id"));
        assertTrue(migration.contains("d.wave_id = NEW.id"));
        assertTrue(migration.contains("ERRCODE = 'check_violation'"));
        assertTrue(migration.contains("BEFORE UPDATE OF status ON courier_dispatch_waves"));
    }

    @Test
    void departureGateDoesNotTrustFreeFormExceptionTextAsAuthorization() throws IOException {
        String migration;
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/V19__courier_dispatch_wave_departure_readiness.sql")) {
            assertNotNull(input);
            migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertFalse(migration.contains("NEW.authorized_exception_reason"));
        assertFalse(migration.contains("authorized_exception_reason IS NOT NULL"));
    }
}
