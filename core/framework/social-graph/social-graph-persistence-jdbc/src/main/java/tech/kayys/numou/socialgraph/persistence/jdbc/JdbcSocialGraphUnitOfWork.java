package tech.kayys.numou.socialgraph.persistence.jdbc;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.domain.SocialConnection;
import tech.kayys.numou.socialgraph.domain.SocialConnectionId;
import tech.kayys.numou.socialgraph.domain.SocialConnectionStatus;
import tech.kayys.numou.socialgraph.spi.SocialGraphIdempotencyRecord;
import tech.kayys.numou.socialgraph.spi.SocialGraphOutboxEvent;
import tech.kayys.numou.socialgraph.spi.SocialGraphTransaction;
import tech.kayys.numou.socialgraph.spi.SocialGraphUnitOfWork;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * JDBC transaction adapter. All graph state, idempotency and outbox writes
 * performed by the callback use the same physical PostgreSQL connection.
 *
 * <p>Call this blocking adapter from a worker thread, not an event-loop thread.
 */
@ApplicationScoped
public class JdbcSocialGraphUnitOfWork implements SocialGraphUnitOfWork {
    private final DataSource dataSource;

    @Inject
    public JdbcSocialGraphUnitOfWork(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    @Override
    public <T> T execute(Function<SocialGraphTransaction, T> work) {
        Objects.requireNonNull(work, "work cannot be null");
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.apply(new Transaction(connection));
                connection.commit();
                return result;
            } catch (RuntimeException | Error failure) {
                rollback(connection, failure);
                throw failure;
            } catch (SQLException failure) {
                rollback(connection, failure);
                throw persistenceFailure("commit social graph transaction", failure);
            } finally {
                try { connection.setAutoCommit(previousAutoCommit); }
                catch (SQLException ignored) { /* connection is closed by try-with-resources */ }
            }
        } catch (SQLException failure) {
            throw persistenceFailure("execute social graph transaction", failure);
        }
    }

    private static final class Transaction implements SocialGraphTransaction {
        private final Connection connection;

        private Transaction(Connection connection) { this.connection = connection; }

        @Override
        public Optional<SocialConnection> findConnection(TenantId tenantId, SocialConnectionId id) {
            String sql = "SELECT * FROM sg_connection WHERE tenant_id = ? AND connection_id = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, tenantId.value());
                ps.setObject(2, id.value());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(readConnection(rs)) : Optional.empty();
                }
            } catch (SQLException e) { throw persistenceFailure("find social connection", e); }
        }

        @Override
        public Optional<SocialConnection> findOpenConnection(TenantId tenantId, PartyId first, PartyId second) {
            UUID low = first.value().compareTo(second.value()) < 0 ? first.value() : second.value();
            UUID high = first.value().compareTo(second.value()) < 0 ? second.value() : first.value();
            String sql = "SELECT * FROM sg_connection WHERE tenant_id = ? AND pair_low = ? AND pair_high = ? "
                    + "AND status IN ('PENDING', 'ACTIVE') FOR UPDATE";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, tenantId.value());
                ps.setObject(2, low);
                ps.setObject(3, high);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(readConnection(rs)) : Optional.empty();
                }
            } catch (SQLException e) { throw persistenceFailure("find open social connection", e); }
        }

        @Override
        public void insertConnection(SocialConnection c) {
            UUID low = c.requesterPartyId().value().compareTo(c.recipientPartyId().value()) < 0
                    ? c.requesterPartyId().value() : c.recipientPartyId().value();
            UUID high = c.requesterPartyId().value().compareTo(c.recipientPartyId().value()) < 0
                    ? c.recipientPartyId().value() : c.requesterPartyId().value();
            String sql = "INSERT INTO sg_connection (tenant_id, connection_id, requester_party_id, recipient_party_id, "
                    + "pair_low, pair_high, status, requested_at, updated_at, resolved_at, version) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, c.tenantId().value());
                ps.setObject(2, c.id().value());
                ps.setObject(3, c.requesterPartyId().value());
                ps.setObject(4, c.recipientPartyId().value());
                ps.setObject(5, low);
                ps.setObject(6, high);
                ps.setString(7, c.status().name());
                ps.setTimestamp(8, Timestamp.from(c.requestedAt()));
                ps.setTimestamp(9, Timestamp.from(c.updatedAt()));
                ps.setTimestamp(10, null);
                ps.setInt(11, c.version());
                ps.executeUpdate();
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new IllegalStateException("A pending or active connection already exists between these Parties", e);
                }
                throw persistenceFailure("insert social connection", e);
            }
        }

        @Override
        public void updateConnection(SocialConnection c, int expectedVersion) {
            String sql = "UPDATE sg_connection SET status = ?, updated_at = ?, resolved_at = ?, version = ? "
                    + "WHERE tenant_id = ? AND connection_id = ? AND version = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, c.status().name());
                ps.setTimestamp(2, Timestamp.from(c.updatedAt()));
                ps.setTimestamp(3, Timestamp.from(c.resolvedAt()));
                ps.setInt(4, c.version());
                ps.setObject(5, c.tenantId().value());
                ps.setObject(6, c.id().value());
                ps.setInt(7, expectedVersion);
                if (ps.executeUpdate() != 1) {
                    throw new SocialGraphOptimisticConflictException("Social connection changed concurrently");
                }
            } catch (SQLException e) { throw persistenceFailure("update social connection", e); }
        }

        @Override
        public Optional<SocialGraphIdempotencyRecord> findIdempotency(TenantId tenantId, String key) {
            String sql = "SELECT command_fingerprint, connection_id, created_at FROM sg_command_idempotency "
                    + "WHERE tenant_id = ? AND idempotency_key = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, tenantId.value());
                ps.setString(2, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(new SocialGraphIdempotencyRecord(tenantId, key,
                            rs.getString("command_fingerprint"),
                            SocialConnectionId.of(rs.getObject("connection_id", UUID.class)),
                            rs.getTimestamp("created_at").toInstant()));
                }
            } catch (SQLException e) { throw persistenceFailure("find social graph idempotency record", e); }
        }

        @Override
        public void saveIdempotency(SocialGraphIdempotencyRecord record) {
            String sql = "INSERT INTO sg_command_idempotency "
                    + "(tenant_id, idempotency_key, command_fingerprint, connection_id, created_at) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, record.tenantId().value());
                ps.setString(2, record.key());
                ps.setString(3, record.fingerprint());
                ps.setObject(4, record.connectionId().value());
                ps.setTimestamp(5, Timestamp.from(record.createdAt()));
                ps.executeUpdate();
            } catch (SQLException e) { throw persistenceFailure("save social graph idempotency record", e); }
        }

        @Override
        public void appendOutbox(SocialGraphOutboxEvent event) {
            String sql = "INSERT INTO sg_outbox (event_id, tenant_id, aggregate_id, aggregate_version, event_type, "
                    + "schema_version, occurred_at, payload) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, event.eventId());
                ps.setObject(2, event.tenantId().value());
                ps.setObject(3, event.aggregateId().value());
                ps.setInt(4, event.aggregateVersion());
                ps.setString(5, event.eventType());
                ps.setInt(6, event.schemaVersion());
                ps.setTimestamp(7, Timestamp.from(event.occurredAt()));
                ps.setString(8, event.payloadJson());
                ps.executeUpdate();
            } catch (SQLException e) { throw persistenceFailure("append social graph outbox event", e); }
        }

        private static SocialConnection readConnection(ResultSet rs) throws SQLException {
            Timestamp resolved = rs.getTimestamp("resolved_at");
            return SocialConnection.rehydrate(
                    SocialConnectionId.of(rs.getObject("connection_id", UUID.class)),
                    TenantId.of(rs.getObject("tenant_id", UUID.class)),
                    new PartyId(rs.getObject("requester_party_id", UUID.class)),
                    new PartyId(rs.getObject("recipient_party_id", UUID.class)),
                    SocialConnectionStatus.valueOf(rs.getString("status")),
                    rs.getTimestamp("requested_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant(),
                    resolved == null ? null : resolved.toInstant(),
                    rs.getInt("version"));
        }
    }

    private static void rollback(Connection connection, Throwable original) {
        try { connection.rollback(); }
        catch (SQLException rollbackFailure) { original.addSuppressed(rollbackFailure); }
    }

    private static RuntimeException persistenceFailure(String operation, SQLException cause) {
        return new IllegalStateException("Unable to " + operation, cause);
    }
}
