package tech.kayys.numou.courier.adapter.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.courier.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class JdbcShipmentRepositoryIdempotencyIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("courier_repository")
            .withUsername("courier")
            .withPassword("courier");

    private static DataSource dataSource;
    private static JdbcShipmentRepository repository;

    @BeforeAll
    static void migrateAndCreateRepository() {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        dataSource = source;
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        repository = new JdbcShipmentRepository(dataSource);
    }

    @Test
    void retryWithSameTenantKeyAndFingerprintReturnsOriginalShipment() {
        TenantId tenant = TenantId.generate();
        Shipment original = bookedShipment(tenant, "SHIP-IDEM-001", ParcelId.generate(), "PARCEL-001");
        Shipment first = repository.saveIdempotently(tenant, "booking-001", fingerprint("same"), original)
                .toCompletableFuture().join();

        Shipment retryPayload = bookedShipment(tenant, "SHIP-IDEM-001", ParcelId.generate(), "PARCEL-001");
        Shipment replay = repository.saveIdempotently(tenant, "booking-001", fingerprint("same"), retryPayload)
                .toCompletableFuture().join();

        assertEquals(first.id(), replay.id());
        assertNotEquals(retryPayload.id(), replay.id());
        assertEquals(1, shipmentCount(tenant));
        assertEquals(1, replay.parcels().size());
    }

    @Test
    void retryWithDifferentFingerprintIsRejectedWithoutWritingAnotherShipment() {
        TenantId tenant = TenantId.generate();
        Shipment original = bookedShipment(tenant, "SHIP-IDEM-002", ParcelId.generate(), "PARCEL-002");
        repository.saveIdempotently(tenant, "booking-002", fingerprint("original"), original)
                .toCompletableFuture().join();

        Shipment changed = bookedShipment(tenant, "SHIP-IDEM-003", ParcelId.generate(), "PARCEL-003");
        CompletionException failure = assertThrows(CompletionException.class, () ->
                repository.saveIdempotently(tenant, "booking-002", fingerprint("changed"), changed)
                        .toCompletableFuture().join());

        assertInstanceOf(JdbcShipmentRepository.ShipmentBookingIdempotencyConflictException.class,
                failure.getCause());
        assertTrue(repository.findById(tenant, changed.id()).toCompletableFuture().join().isEmpty());
        assertEquals(1, shipmentCount(tenant));
    }

    @Test
    void sameKeyCanBookIndependentlyForDifferentTenants() {
        TenantId tenantA = TenantId.generate();
        TenantId tenantB = TenantId.generate();
        Shipment shipmentA = bookedShipment(tenantA, "SHIP-IDEM-004", ParcelId.generate(), "PARCEL-004");
        Shipment shipmentB = bookedShipment(tenantB, "SHIP-IDEM-004", ParcelId.generate(), "PARCEL-004");

        Shipment savedA = repository.saveIdempotently(tenantA, "shared-key", fingerprint("tenant-a"), shipmentA)
                .toCompletableFuture().join();
        Shipment savedB = repository.saveIdempotently(tenantB, "shared-key", fingerprint("tenant-b"), shipmentB)
                .toCompletableFuture().join();

        assertEquals(shipmentA.id(), savedA.id());
        assertEquals(shipmentB.id(), savedB.id());
        assertNotEquals(savedA.id(), savedB.id());
        assertEquals(1, shipmentCount(tenantA));
        assertEquals(1, shipmentCount(tenantB));
    }

    @Test
    void parcelWriteFailureRollsBackShipmentAndIdempotencyReservation() throws Exception {
        TenantId tenant = TenantId.generate();
        ParcelId sharedParcelId = ParcelId.generate();
        Shipment existing = bookedShipment(tenant, "SHIP-IDEM-005", sharedParcelId, "PARCEL-005");
        repository.save(tenant, existing).toCompletableFuture().join();

        Shipment conflicting = bookedShipment(tenant, "SHIP-IDEM-006", sharedParcelId, "PARCEL-006");
        assertThrows(CompletionException.class, () ->
                repository.saveIdempotently(tenant, "booking-rollback", fingerprint("rollback"), conflicting)
                        .toCompletableFuture().join());

        assertTrue(repository.findById(tenant, conflicting.id()).toCompletableFuture().join().isEmpty());
        assertEquals(1, shipmentCount(tenant));
        assertEquals(0, idempotencyCount(tenant, "booking-rollback"));
    }

    private static Shipment bookedShipment(TenantId tenant, String reference, ParcelId parcelId, String parcelReference) {
        Instant now = Instant.parse("2026-10-29T00:00:00Z");
        Shipment shipment = Shipment.draft(ShipmentId.generate(), tenant, reference,
                new ShipmentAddress("Sender", "+6222111", "Origin Street 1", null, "Bandung", "West Java", "40111", "ID"),
                new ShipmentAddress("Recipient", "+6222222", "Destination Street 2", null, "Jakarta", "DKI Jakarta", "10110", "ID"),
                ServiceLevel.STANDARD, now);
        shipment.addParcel(new Parcel(parcelId, parcelReference, new BigDecimal("1.25"),
                new BigDecimal("20"), new BigDecimal("15"), new BigDecimal("10"), null), now);
        shipment.book(now.plusSeconds(1));
        return shipment;
    }

    private static int shipmentCount(TenantId tenant) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT count(*) FROM courier_shipments WHERE tenant_id = ?")) {
            statement.setObject(1, tenant.value());
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to count tenant shipments", failure);
        }
    }

    private static int idempotencyCount(TenantId tenant, String key) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT count(*) FROM courier_shipment_booking_keys WHERE tenant_id = ? AND idempotency_key = ?")) {
            statement.setObject(1, tenant.value());
            statement.setString(2, key);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to count idempotency reservations", failure);
        }
    }

    private static String fingerprint(String value) {
        try {
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
