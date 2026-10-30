package tech.kayys.numou.affiliate.postgres;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.kayys.numou.affiliate.domain.PartnerPayout;
import tech.kayys.numou.affiliate.domain.PartnerPayoutStatus;
import tech.kayys.numou.affiliate.domain.SettlementBatch;
import tech.kayys.numou.affiliate.domain.SettlementBatchItem;
import tech.kayys.numou.affiliate.domain.SettlementBatchStatus;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresSettlementBatchRepositoryTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private DataSource dataSource;
    private PostgresSettlementBatchRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = dataSource();
        repository = new PostgresSettlementBatchRepository(dataSource);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS affiliate_event_outbox CASCADE");
            statement.execute("DROP TABLE IF EXISTS affiliate_audit_event CASCADE");
            statement.execute("DROP TABLE IF EXISTS affiliate_settlement_payout_reservation CASCADE");
            statement.execute("DROP TABLE IF EXISTS affiliate_settlement_batch_item CASCADE");
            statement.execute("DROP TABLE IF EXISTS affiliate_settlement_batch CASCADE");
            statement.execute("DROP TABLE IF EXISTS affiliate_partner_payout CASCADE");
        }
        applyMigration("V1__affiliate_payout_and_settlement_storage.sql");
        applyMigration("V2__affiliate_audit_and_event_outbox.sql");
    }

    @Test
    void persistsBatchAndItemsAndScopesReadsByTenant() throws Exception {
        UUID payoutId = payout("tenant-a", "100.00");
        SettlementBatch proposed = batch("tenant-a", "batch-key-1",
                List.of(item(payoutId, "100.00")), "100.00");

        SettlementBatch saved = repository.saveOrGet(proposed).toCompletableFuture().join();

        assertEquals(proposed.id(), saved.id());
        assertEquals(1, count("affiliate_settlement_batch", "tenant-a", saved.id()));
        assertEquals(1, countBatchItems("tenant-a", saved.id()));
        assertEquals(1, countReservations("tenant-a", payoutId));
        assertEquals(1, countEventRows("affiliate_audit_event", "tenant-a", saved.id()));
        assertEquals(1, countEventRows("affiliate_event_outbox", "tenant-a", saved.id()));

        Optional<SettlementBatch> sameTenant = repository.findById("tenant-a", saved.id())
                .toCompletableFuture().join();
        Optional<SettlementBatch> otherTenant = repository.findById("tenant-b", saved.id())
                .toCompletableFuture().join();
        assertTrue(sameTenant.isPresent());
        assertTrue(otherTenant.isEmpty());

        SettlementBatch replay = batch("tenant-a", "batch-key-1",
                List.of(item(payoutId, "100.00")), "100.00");
        SettlementBatch replayed = repository.saveOrGet(replay).toCompletableFuture().join();
        assertEquals(saved.id(), replayed.id());
        assertEquals(1, count("affiliate_settlement_batch", "tenant-a", saved.id()));
    }

    @Test
    void rejectsIllegalPayoutLifecycleRegressionAndStaleIntentMutation() throws Exception {
        UUID payoutId = payout("tenant-a", "100.00");
        PostgresPartnerPayoutRepository payouts = new PostgresPartnerPayoutRepository(dataSource);
        PartnerPayout original = payouts.findById("tenant-a", payoutId).toCompletableFuture().join().orElseThrow();

        PartnerPayout submitted = original.submitted("provider-ref-1", original.updatedAt().plusSeconds(1));
        PartnerPayout stored = payouts.update("tenant-a", payoutId, submitted, "operator-42").toCompletableFuture().join();
        assertEquals(PartnerPayoutStatus.SUBMITTED, stored.status());
        assertEquals("operator-42", auditActor("tenant-a", payoutId));

        PartnerPayout illegalRegression = new PartnerPayout(original.id(), original.tenantId(),
                original.partnerId(), original.commissionEntryId(), original.destinationId(), original.currency(),
                original.amount(), original.idempotencyKey(), original.correlationId(), PartnerPayoutStatus.REQUESTED,
                original.attempts(), null, null, original.createdAt(), stored.updatedAt().plusSeconds(1));
        assertThrows(RuntimeException.class,
                () -> payouts.update("tenant-a", payoutId, illegalRegression).toCompletableFuture().join());

        PartnerPayout changedAmount = new PartnerPayout(stored.id(), stored.tenantId(), stored.partnerId(),
                stored.commissionEntryId(), stored.destinationId(), stored.currency(), new BigDecimal("101.00"),
                stored.idempotencyKey(), stored.correlationId(), PartnerPayoutStatus.PAID, stored.attempts() + 1,
                "provider-ref-1", null, stored.createdAt(), stored.updatedAt().plusSeconds(2));
        assertThrows(RuntimeException.class,
                () -> payouts.update("tenant-a", payoutId, changedAmount).toCompletableFuture().join());
    }

    @Test
    void rollsBackBatchAndAllReservationsWhenOnePayoutIsAlreadyReserved() throws Exception {
        UUID reservedPayout = payout("tenant-a", "100.00");
        UUID freshPayout = payout("tenant-a", "40.00");
        SettlementBatch first = batch("tenant-a", "batch-key-1",
                List.of(item(reservedPayout, "100.00")), "100.00");
        repository.saveOrGet(first).toCompletableFuture().join();

        SettlementBatch conflicting = batch("tenant-a", "batch-key-2",
                List.of(item(freshPayout, "40.00"), item(reservedPayout, "100.00")), "140.00");
        assertThrows(RuntimeException.class, () -> repository.saveOrGet(conflicting).toCompletableFuture().join());

        assertEquals(0, count("affiliate_settlement_batch", "tenant-a", conflicting.id()));
        assertEquals(0, countBatchItems("tenant-a", conflicting.id()));
        assertEquals(0, countReservations("tenant-a", freshPayout));
        assertEquals(1, countReservations("tenant-a", reservedPayout));
    }


    @Test
    void outboxClaimsAreTenantScopedAndExpiredLeasesCanBeReclaimed() throws Exception {
        UUID payoutId = payout("tenant-a", "25.00");
        SettlementBatch proposed = batch("tenant-a", "outbox-key",
                List.of(item(payoutId, "25.00")), "25.00");
        repository.saveOrGet(proposed).toCompletableFuture().join();

        PostgresAffiliateOutboxRepository outbox = new PostgresAffiliateOutboxRepository(dataSource);
        Instant now = Instant.parse("2026-03-01T00:00:00Z");
        assertTrue(outbox.claimPending("tenant-b", "worker-b", now, Duration.ofSeconds(30), 10)
                .toCompletableFuture().join().isEmpty());

        var first = outbox.claimPending("tenant-a", "worker-a", now, Duration.ofSeconds(30), 10)
                .toCompletableFuture().join();
        assertEquals(1, first.size());
        assertEquals("SettlementBatchCreated", first.get(0).eventType());

        var blocked = outbox.claimPending("tenant-a", "worker-b", now.plusSeconds(10),
                Duration.ofSeconds(30), 10).toCompletableFuture().join();
        assertTrue(blocked.isEmpty());

        var reclaimed = outbox.claimPending("tenant-a", "worker-b", now.plusSeconds(31),
                Duration.ofSeconds(30), 10).toCompletableFuture().join();
        assertEquals(1, reclaimed.size());
        assertEquals(2, reclaimed.get(0).attempts());

        assertThrows(RuntimeException.class, () -> outbox.markDelivered("tenant-a",
                reclaimed.get(0).id(), "worker-a", now.plusSeconds(32)).toCompletableFuture().join());
        outbox.markDelivered("tenant-a", reclaimed.get(0).id(), "worker-b", now.plusSeconds(32))
                .toCompletableFuture().join();
        assertTrue(outbox.claimPending("tenant-a", "worker-c", now.plusSeconds(40),
                Duration.ofSeconds(30), 10).toCompletableFuture().join().isEmpty());
    }

    private String auditActor(String tenant, UUID payoutId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT actor_id FROM affiliate_audit_event WHERE tenant_id = ? AND aggregate_id = ?")) {
            statement.setString(1, tenant);
            statement.setObject(2, payoutId);
            try (ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next(), "payout audit event should exist");
                return rs.getString(1);
            }
        }
    }

    private int countEventRows(String table, String tenant, UUID aggregateId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ? AND aggregate_id = ?")) {
            statement.setString(1, tenant);
            statement.setObject(2, aggregateId);
            try (ResultSet rs = statement.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private UUID payout(String tenant, String amount) throws SQLException {
        UUID id = UUID.randomUUID();
        UUID partner = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        String sql = "INSERT INTO affiliate_partner_payout "
                + "(payout_id, tenant_id, partner_id, commission_entry_id, destination_id, currency, amount, "
                + "idempotency_key, correlation_id, payout_status, attempts, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, 'IDR', ?, ?, ?, 'REQUESTED', 0, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.setString(2, tenant);
            statement.setObject(3, partner);
            statement.setObject(4, UUID.randomUUID());
            statement.setString(5, "verified-destination");
            statement.setBigDecimal(6, new BigDecimal(amount));
            statement.setString(7, "payout-key-" + id);
            statement.setString(8, "correlation-" + id);
            statement.setObject(9, java.sql.Timestamp.from(now));
            statement.setObject(10, java.sql.Timestamp.from(now));
            statement.executeUpdate();
        }
        return id;
    }

    private SettlementBatchItem item(UUID payoutId, String amount) {
        return new SettlementBatchItem(payoutId, partnerFor(payoutId), new BigDecimal(amount), "IDR");
    }

    private UUID partnerFor(UUID payoutId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT partner_id FROM affiliate_partner_payout WHERE payout_id = ?")) {
            statement.setObject(1, payoutId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("test payout missing");
                return rs.getObject(1, UUID.class);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private SettlementBatch batch(String tenant, String key, List<SettlementBatchItem> items, String total) {
        Instant now = Instant.parse("2026-02-01T00:00:00Z");
        return new SettlementBatch(UUID.randomUUID(), tenant, "IDR", items, new BigDecimal(total), key,
                SettlementBatchStatus.READY_FOR_APPROVAL, "maker-1", null, now, now, 0);
    }

    private int count(String table, String tenant, UUID id) throws SQLException {
        String idColumn = table.equals("affiliate_settlement_batch") ? "batch_id" : "outbox_id";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ? AND " + idColumn + " = ?")) {
            statement.setString(1, tenant);
            statement.setObject(2, id);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int countBatchItems(String tenant, UUID batchId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM affiliate_settlement_batch_item WHERE tenant_id = ? AND batch_id = ?")) {
            statement.setString(1, tenant);
            statement.setObject(2, batchId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int countReservations(String tenant, UUID payoutId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM affiliate_settlement_payout_reservation WHERE tenant_id = ? AND payout_id = ?")) {
            statement.setString(1, tenant);
            statement.setObject(2, payoutId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void applyMigration(String filename) throws IOException, SQLException {
        String resource = "db/migration/" + filename;
        String sql;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, "Missing migration: " + resource);
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        // SQL migrations contain explanatory comments; remove them before splitting statements.
        // Otherwise a semicolon in prose can be sent to PostgreSQL as a bogus SQL fragment.
        String executableSql = sql.replaceAll("(?m)^\\s*--.*(?:\\R|$)", "");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String part : executableSql.split(";")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) statement.execute(trimmed);
            }
        }
    }

    private DataSource dataSource() {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        return source;
    }
}
