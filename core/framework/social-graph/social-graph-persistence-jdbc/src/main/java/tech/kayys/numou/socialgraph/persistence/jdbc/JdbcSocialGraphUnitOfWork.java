package tech.kayys.numou.socialgraph.persistence.jdbc;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.domain.SocialConnection;
import tech.kayys.numou.socialgraph.domain.SocialConnectionId;
import tech.kayys.numou.socialgraph.domain.SocialConnectionStatus;
import tech.kayys.numou.socialgraph.domain.SocialRelationship;
import tech.kayys.numou.socialgraph.domain.SocialRelationshipId;
import tech.kayys.numou.socialgraph.domain.SocialRelationshipStatus;
import tech.kayys.numou.socialgraph.domain.SocialRelationshipType;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * JDBC transaction adapter. All graph state, idempotency and outbox writes
 * performed by the callback use the same physical PostgreSQL connection.
 *
 * <p>Blocking JDBC work is scheduled on Mutiny's default worker pool so callers
 * can compose this port without blocking an event-loop thread.
 */
@ApplicationScoped
public class JdbcSocialGraphUnitOfWork implements SocialGraphUnitOfWork {
    private final DataSource dataSource;

    @Inject
    public JdbcSocialGraphUnitOfWork(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    @Override
    public <T> Uni<T> execute(Function<SocialGraphTransaction, T> work) {
        Objects.requireNonNull(work, "work cannot be null");
        return Uni.createFrom().item(() -> executeBlocking(work))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private <T> T executeBlocking(Function<SocialGraphTransaction, T> work) {
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
        public Optional<SocialRelationship> findRelationship(TenantId tenantId, SocialRelationshipId id) {
            String sql = "SELECT * FROM sg_relationship WHERE tenant_id = ? AND relationship_id = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, tenantId.value());
                ps.setObject(2, id.value());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(readRelationship(rs)) : Optional.empty();
                }
            } catch (SQLException e) { throw persistenceFailure("find social relationship", e); }
        }

        @Override
        public Optional<SocialRelationship> findActiveRelationship(
                TenantId tenantId, SocialRelationshipType type, PartyId source, PartyId target) {
            String sql = "SELECT * FROM sg_relationship WHERE tenant_id = ? AND relationship_type = ? "
                    + "AND source_party_id = ? AND target_party_id = ? AND status = 'ACTIVE' FOR UPDATE";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, tenantId.value());
                ps.setString(2, type.name());
                ps.setObject(3, source.value());
                ps.setObject(4, target.value());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(readRelationship(rs)) : Optional.empty();
                }
            } catch (SQLException e) { throw persistenceFailure("find active social relationship", e); }
        }

        @Override
        public void insertRelationship(SocialRelationship relationship) {
            String sql = "INSERT INTO sg_relationship (tenant_id, relationship_id, relationship_type, "
                    + "source_party_id, target_party_id, status, created_at, updated_at, valid_from, valid_until, "
                    + "removed_at, provenance, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, relationship.tenantId().value());
                ps.setObject(2, relationship.id().value());
                ps.setString(3, relationship.type().name());
                ps.setObject(4, relationship.sourcePartyId().value());
                ps.setObject(5, relationship.targetPartyId().value());
                ps.setString(6, relationship.status().name());
                ps.setTimestamp(7, Timestamp.from(relationship.createdAt()));
                ps.setTimestamp(8, Timestamp.from(relationship.updatedAt()));
                ps.setTimestamp(9, Timestamp.from(relationship.validFrom()));
                ps.setTimestamp(10, relationship.validUntil() == null ? null : Timestamp.from(relationship.validUntil()));
                ps.setTimestamp(11, relationship.removedAt() == null ? null : Timestamp.from(relationship.removedAt()));
                ps.setString(12, relationship.provenance());
                ps.setInt(13, relationship.version());
                ps.executeUpdate();
                saveRelationshipMetadata(relationship);
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new IllegalStateException("An active relationship of this type already exists for this Party pair", e);
                }
                throw persistenceFailure("insert social relationship", e);
            }
        }

        @Override
        public void updateRelationship(SocialRelationship relationship, int expectedVersion) {
            String sql = "UPDATE sg_relationship SET status = ?, updated_at = ?, valid_from = ?, valid_until = ?, "
                    + "removed_at = ?, provenance = ?, version = ? "
                    + "WHERE tenant_id = ? AND relationship_id = ? AND version = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, relationship.status().name());
                ps.setTimestamp(2, Timestamp.from(relationship.updatedAt()));
                ps.setTimestamp(3, Timestamp.from(relationship.validFrom()));
                ps.setTimestamp(4, relationship.validUntil() == null ? null : Timestamp.from(relationship.validUntil()));
                ps.setTimestamp(5, relationship.removedAt() == null ? null : Timestamp.from(relationship.removedAt()));
                ps.setString(6, relationship.provenance());
                ps.setInt(7, relationship.version());
                ps.setObject(8, relationship.tenantId().value());
                ps.setObject(9, relationship.id().value());
                ps.setInt(10, expectedVersion);
                if (ps.executeUpdate() != 1) {
                    throw new SocialGraphOptimisticConflictException("Social relationship changed concurrently");
                }
                replaceRelationshipMetadata(relationship);
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new IllegalStateException("An active relationship of this type already exists for this Party pair", e);
                }
                throw persistenceFailure("update social relationship", e);
            }
        }

        private SocialRelationship readRelationship(ResultSet rs) throws SQLException {
            Timestamp validUntil = rs.getTimestamp("valid_until");
            Timestamp removedAt = rs.getTimestamp("removed_at");
            return SocialRelationship.rehydrate(
                    SocialRelationshipId.of(rs.getObject("relationship_id", UUID.class)),
                    TenantId.of(rs.getObject("tenant_id", UUID.class)),
                    SocialRelationshipType.valueOf(rs.getString("relationship_type")),
                    new PartyId(rs.getObject("source_party_id", UUID.class)),
                    new PartyId(rs.getObject("target_party_id", UUID.class)),
                    SocialRelationshipStatus.valueOf(rs.getString("status")),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant(),
                    rs.getTimestamp("valid_from").toInstant(),
                    validUntil == null ? null : validUntil.toInstant(),
                    removedAt == null ? null : removedAt.toInstant(),
                    rs.getString("provenance"),
                    loadRelationshipMetadata(
                            TenantId.of(rs.getObject("tenant_id", UUID.class)),
                            SocialRelationshipId.of(rs.getObject("relationship_id", UUID.class))),
                    rs.getInt("version"));
        }

        private void saveRelationshipMetadata(SocialRelationship relationship) throws SQLException {
            String sql = "INSERT INTO sg_relationship_metadata "
                    + "(tenant_id, relationship_id, metadata_key, metadata_value) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (var entry : relationship.metadata().entrySet()) {
                    ps.setObject(1, relationship.tenantId().value());
                    ps.setObject(2, relationship.id().value());
                    ps.setString(3, entry.getKey());
                    ps.setString(4, entry.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        private void replaceRelationshipMetadata(SocialRelationship relationship) throws SQLException {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM sg_relationship_metadata WHERE tenant_id = ? AND relationship_id = ?")) {
                delete.setObject(1, relationship.tenantId().value());
                delete.setObject(2, relationship.id().value());
                delete.executeUpdate();
            }
            saveRelationshipMetadata(relationship);
        }

        private Map<String, String> loadRelationshipMetadata(TenantId tenantId, SocialRelationshipId id)
                throws SQLException {
            Map<String, String> metadata = new java.util.TreeMap<>();
            String sql = "SELECT metadata_key, metadata_value FROM sg_relationship_metadata "
                    + "WHERE tenant_id = ? AND relationship_id = ? ORDER BY metadata_key";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, tenantId.value());
                ps.setObject(2, id.value());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) metadata.put(rs.getString(1), rs.getString(2));
                }
            }
            return Map.copyOf(metadata);
        }

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
            boolean firstComesFirst = comparePartyIds(first.value(), second.value()) < 0;
            UUID low = firstComesFirst ? first.value() : second.value();
            UUID high = firstComesFirst ? second.value() : first.value();
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
            boolean requesterComesFirst = comparePartyIds(
                    c.requesterPartyId().value(), c.recipientPartyId().value()) < 0;
            UUID low = requesterComesFirst ? c.requesterPartyId().value() : c.recipientPartyId().value();
            UUID high = requesterComesFirst ? c.recipientPartyId().value() : c.requesterPartyId().value();
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
        public void appendAudit(tech.kayys.numou.socialgraph.spi.SocialGraphAuditRecord record) {
            String sql = "INSERT INTO sg_connection_audit (audit_id, tenant_id, connection_id, actor_party_id, "
                    + "from_status, to_status, action, idempotency_key, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, record.auditId());
                ps.setObject(2, record.tenantId().value());
                ps.setObject(3, record.connectionId().value());
                ps.setObject(4, record.actorPartyId().value());
                ps.setString(5, record.fromStatus() == null ? null : record.fromStatus().name());
                ps.setString(6, record.toStatus().name());
                ps.setString(7, record.action());
                ps.setString(8, record.idempotencyKey());
                ps.setTimestamp(9, Timestamp.from(record.occurredAt()));
                ps.executeUpdate();
            } catch (SQLException e) { throw persistenceFailure("append social graph audit record", e); }
        }

        @Override
        public void appendOutbox(SocialGraphOutboxEvent event) {
            String sql = "INSERT INTO sg_outbox (event_id, tenant_id, actor_party_id, aggregate_id, aggregate_version, event_type, "
                    + "schema_version, occurred_at, payload) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, event.eventId());
                ps.setObject(2, event.tenantId().value());
                ps.setObject(3, event.actorPartyId().value());
                ps.setObject(4, event.aggregateId().value());
                ps.setInt(5, event.aggregateVersion());
                ps.setString(6, event.eventType());
                ps.setInt(7, event.schemaVersion());
                ps.setTimestamp(8, Timestamp.from(event.occurredAt()));
                ps.setString(9, event.payloadJson());
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

    /**
     * Uses the same canonical ordering as PostgreSQL's UUID comparison and the
     * pair_low < pair_high database constraint. UUID.compareTo is signed-long
     * based and can disagree with PostgreSQL's bytewise UUID ordering.
     */
    private static int comparePartyIds(UUID first, UUID second) {
        return first.toString().compareTo(second.toString());
    }

    private static void rollback(Connection connection, Throwable original) {
        try { connection.rollback(); }
        catch (SQLException rollbackFailure) { original.addSuppressed(rollbackFailure); }
    }

    private static RuntimeException persistenceFailure(String operation, SQLException cause) {
        return new IllegalStateException("Unable to " + operation, cause);
    }
}
