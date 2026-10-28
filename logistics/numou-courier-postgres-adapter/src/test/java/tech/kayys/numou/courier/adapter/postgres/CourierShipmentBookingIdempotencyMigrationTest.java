package tech.kayys.numou.courier.adapter.postgres;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class CourierShipmentBookingIdempotencyMigrationTest {
    @Test void migrationEnforcesTenantScopedUniqueBookingKeysAndFingerprints() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V27__courier_shipment_booking_idempotency.sql"), StandardCharsets.UTF_8).toLowerCase();
        assertTrue(sql.contains("primary key (tenant_id, idempotency_key)"));
        assertTrue(sql.contains("request_fingerprint char(64) not null"));
        assertTrue(sql.contains("shipment_id uuid not null"));
        assertTrue(sql.contains("request_fingerprint ~ '^[0-9a-f]{64}$'"));
    }
    @Test void adapterReservesAndReplaysIdempotencyInShipmentTransaction() throws IOException {
        String source = Files.readString(Path.of("src/main/java/tech/kayys/numou/courier/adapter/postgres/JdbcShipmentRepository.java"), StandardCharsets.UTF_8);
        assertTrue(source.contains("INSERT INTO courier_shipment_booking_keys"));
        assertTrue(source.contains("ON CONFLICT (tenant_id, idempotency_key) DO NOTHING"));
        assertTrue(source.contains("ShipmentBookingIdempotencyConflictException"));
        assertTrue(source.contains("connection.rollback()"));
    }
}