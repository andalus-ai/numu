package tech.kayys.numou.courier.adapter.postgres;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class CourierShipmentBookingIdempotencyPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("courier_idempotency")
            .withUsername("courier")
            .withPassword("courier");

    @BeforeAll
    static void applyMigration() throws IOException, SQLException {
        String migration;
        try (InputStream stream = CourierShipmentBookingIdempotencyPostgresTest.class
                .getResourceAsStream("/db/migration/V27__courier_shipment_booking_idempotency.sql")) {
            assertNotNull(stream, "V27 migration must be on the test classpath");
            migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            for (String command : migration.split(";")) {
                if (!command.isBlank()) statement.execute(command);
            }
        }
    }

    @Test
    void idempotencyKeyIsUniqueWithinTenantButReusableByAnotherTenant() throws SQLException {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        try (Connection connection = connection()) {
            assertEquals(1, reserve(connection, tenantA, "booking-1", fingerprint('a'), UUID.randomUUID()));
            assertEquals(1, reserve(connection, tenantB, "booking-1", fingerprint('b'), UUID.randomUUID()));
            assertEquals(0, reserve(connection, tenantA, "booking-1", fingerprint('a'), UUID.randomUUID()));
            assertEquals(2, countRows(connection, "booking-1"));
        }
    }

    @Test
    void reservationRollsBackWithTheOwningTransaction() throws SQLException {
        UUID tenant = UUID.randomUUID();
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            reserve(connection, tenant, "rollback-me", fingerprint('c'), UUID.randomUUID());
            connection.rollback();
            connection.setAutoCommit(true);

            assertEquals(0, countRows(connection, "rollback-me"));
            assertEquals(1, reserve(connection, tenant, "rollback-me", fingerprint('c'), UUID.randomUUID()));
        }
    }

    @Test
    void concurrentReservationsForOneTenantAndKeyHaveExactlyOneWinner() throws Exception {
        UUID tenant = UUID.randomUUID();
        try (Connection first = connection(); Connection second = connection();
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            first.setAutoCommit(false);
            assertEquals(1, reserve(first, tenant, "concurrent-key", fingerprint('d'), UUID.randomUUID()));

            CountDownLatch attemptingInsert = new CountDownLatch(1);
            Future<Integer> contender = executor.submit(() -> {
                attemptingInsert.countDown();
                return reserve(second, tenant, "concurrent-key", fingerprint('d'), UUID.randomUUID());
            });
            assertTrue(attemptingInsert.await(5, TimeUnit.SECONDS), "contender should start");
            first.commit();

            assertEquals(0, contender.get(10, TimeUnit.SECONDS));
            assertEquals(1, countRows(second, "concurrent-key"));
        }
    }

    @Test
    void databaseRejectsMalformedFingerprintsAndBlankKeys() throws SQLException {
        try (Connection connection = connection()) {
            assertThrows(SQLException.class, () -> reserve(connection, UUID.randomUUID(),
                    "bad-fingerprint", "not-a-sha", UUID.randomUUID()));
            assertThrows(SQLException.class, () -> reserve(connection, UUID.randomUUID(),
                    "   ", fingerprint('e'), UUID.randomUUID()));
        }
    }

    private static int reserve(Connection connection, UUID tenantId, String key, String fingerprint,
                               UUID shipmentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO courier_shipment_booking_keys
                  (tenant_id, idempotency_key, request_fingerprint, shipment_id)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                """)) {
            statement.setObject(1, tenantId);
            statement.setString(2, key);
            statement.setString(3, fingerprint);
            statement.setObject(4, shipmentId);
            return statement.executeUpdate();
        }
    }

    private static int countRows(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM courier_shipment_booking_keys WHERE idempotency_key = ?")) {
            statement.setString(1, key);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String fingerprint(char value) {
        return String.valueOf(value).repeat(64);
    }
}
