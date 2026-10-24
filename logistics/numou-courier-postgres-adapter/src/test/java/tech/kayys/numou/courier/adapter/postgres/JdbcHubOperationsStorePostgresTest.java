package tech.kayys.numou.courier.adapter.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.courier.application.port.HubOperationsStore;
import tech.kayys.numou.courier.domain.HubScanCommand;
import tech.kayys.numou.courier.domain.HubScanType;
import tech.kayys.numou.courier.domain.ShipmentId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class JdbcHubOperationsStorePostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static JdbcHubOperationsStore store;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        store = new JdbcHubOperationsStore(() -> DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    @Test
    void sameKeyAndFingerprintAreSafeReplayAndDoNotDuplicateFacts() throws Exception {
        Fixture fixture = fixture();
        HubOperationsStore.ScanReceiptResult first = store.ingest(fixture.command("sha256:a"))
                .toCompletableFuture().join();
        HubOperationsStore.ScanReceiptResult replay = store.ingest(fixture.command("sha256:a"))
                .toCompletableFuture().join();

        assertEquals(HubOperationsStore.Outcome.ACCEPTED, first.outcome());
        assertEquals(HubOperationsStore.Outcome.REPLAYED, replay.outcome());
        assertEquals(first.receiptReference(), replay.receiptReference());
        assertEquals(1, count("courier_hub_scan_receipts", fixture.tenant()));
        assertEquals(1, count("courier_tracking_events", fixture.tenant()));
        assertEquals(1, count("courier_operational_outbox", fixture.tenant()));
    }

    @Test
    void sameKeyWithDifferentFingerprintConflictsWithoutMutation() throws Exception {
        Fixture fixture = fixture();
        store.ingest(fixture.command("sha256:original")).toCompletableFuture().join();

        HubOperationsStore.ScanReceiptResult conflict = store.ingest(fixture.command("sha256:changed"))
                .toCompletableFuture().join();

        assertEquals(HubOperationsStore.Outcome.IDEMPOTENCY_CONFLICT, conflict.outcome());
        assertEquals(1, count("courier_hub_scan_receipts", fixture.tenant()));
        assertEquals(1, count("courier_tracking_events", fixture.tenant()));
        assertEquals(1, count("courier_operational_outbox", fixture.tenant()));
    }

    @Test
    void sameKeyIsScopedByTenant() throws Exception {
        Fixture owner = fixture();
        Fixture other = fixture();
        store.ingest(owner.command("sha256:same")).toCompletableFuture().join();
        HubOperationsStore.ScanReceiptResult independent = store.ingest(other.command("sha256:same"))
                .toCompletableFuture().join();

        assertEquals(HubOperationsStore.Outcome.ACCEPTED, independent.outcome());
        assertEquals(1, count("courier_hub_scan_receipts", owner.tenant()));
        assertEquals(1, count("courier_hub_scan_receipts", other.tenant()));
    }

    @Test
    void concurrentDuplicateDeliveryProducesOneReceiptTrackingFactAndOutboxRow() throws Exception {
        Fixture fixture = fixture();
        CompletableFuture<HubOperationsStore.ScanReceiptResult> a = CompletableFuture.supplyAsync(
                () -> store.ingest(fixture.command("sha256:race")).toCompletableFuture().join());
        CompletableFuture<HubOperationsStore.ScanReceiptResult> b = CompletableFuture.supplyAsync(
                () -> store.ingest(fixture.command("sha256:race")).toCompletableFuture().join());
        CompletableFuture.allOf(a, b).join();

        assertTrue(java.util.Set.of(a.join().outcome(), b.join().outcome()).contains(HubOperationsStore.Outcome.ACCEPTED));
        assertTrue(java.util.Set.of(a.join().outcome(), b.join().outcome()).contains(HubOperationsStore.Outcome.REPLAYED));
        assertEquals(1, count("courier_hub_scan_receipts", fixture.tenant()));
        assertEquals(1, count("courier_tracking_events", fixture.tenant()));
        assertEquals(1, count("courier_operational_outbox", fixture.tenant()));
    }

    private static Fixture fixture() throws Exception {
        TenantId tenant = TenantId.generate();
        ShipmentId shipment = ShipmentId.generate();
        Instant now = Instant.now().minusSeconds(2);
        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             PreparedStatement s = c.prepareStatement("""
                INSERT INTO courier_shipments
                  (id,tenant_id,reference,normalized_reference,status,service_level,
                   sender_contact_name,sender_phone,sender_address_line_1,sender_locality,sender_country_code,
                   recipient_contact_name,recipient_phone,recipient_address_line_1,recipient_locality,
                   recipient_country_code,created_at,updated_at,booked_at)
                VALUES (?,?,?,?,'BOOKED','STANDARD','Sender','+62000','Sender St','Bandung','ID',
                        'Recipient','+62111','Recipient St','Jakarta','ID',?,?,?)
                """)) {
            s.setObject(1, shipment.value());
            s.setObject(2, tenant.value());
            String reference = "HUB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            s.setString(3, reference);
            s.setString(4, reference);
            s.setTimestamp(5, java.sql.Timestamp.from(now));
            s.setTimestamp(6, java.sql.Timestamp.from(now));
            s.setTimestamp(7, java.sql.Timestamp.from(now));
            s.executeUpdate();
        }
        return new Fixture(tenant, shipment, now);
    }

    private static long count(String table, TenantId tenant) throws Exception {
        if (!java.util.Set.of("courier_hub_scan_receipts", "courier_tracking_events",
                "courier_operational_outbox").contains(table)) throw new IllegalArgumentException("table");
        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             PreparedStatement s = c.prepareStatement("SELECT count(*) FROM " + table + " WHERE tenant_id = ?")) {
            s.setObject(1, tenant.value());
            try (ResultSet r = s.executeQuery()) {
                r.next();
                return r.getLong(1);
            }
        }
    }

    private record Fixture(TenantId tenant, ShipmentId shipment, Instant occurredAt) {
        HubScanCommand command(String fingerprint) {
            return new HubScanCommand(tenant, UUID.randomUUID(), "scanner-a", "device-1:scan-1",
                    fingerprint, shipment, "parcel-1", HubScanType.HUB_INTAKE, "hub-jkt",
                    "operator-1", occurredAt, occurredAt.plusSeconds(1));
        }
    }
}
