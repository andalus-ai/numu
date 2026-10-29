package tech.kayys.numou.queuemanagement.postgres;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.queuemanagement.domain.QueueDefinition;
import tech.kayys.numou.queuemanagement.domain.QueueEntry;
import tech.kayys.numou.queuemanagement.domain.QueueEntryId;
import tech.kayys.numou.queuemanagement.domain.QueueEntryStatus;
import tech.kayys.numou.queuemanagement.domain.QueueId;
import tech.kayys.numou.queuemanagement.domain.QueueOrderingPolicy;
import tech.kayys.numou.queuemanagement.domain.QueueProfile;
import tech.kayys.numou.queuemanagement.domain.QueueSourceReference;
import tech.kayys.numou.queuemanagement.domain.QueueStatus;
import tech.kayys.numou.queuemanagement.spi.ClaimMode;
import tech.kayys.numou.queuemanagement.spi.QueueRepositoryPort;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL implementation of queue-owned persistence.
 *
 * Every operation is tenant-qualified and establishes the transaction-local tenant
 * setting required by the module's FORCE ROW LEVEL SECURITY policies. Methods join an
 * ambient JTA transaction when one exists; command orchestration must provide the
 * outer transaction that also persists audit, outbox, and idempotency records.
 */
@ApplicationScoped
public class QueuePostgresRepository implements QueueRepositoryPort {
    private final AgroalDataSource dataSource;

    @Inject
    public QueuePostgresRepository(AgroalDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    @Transactional
    public Optional<QueueDefinition> findQueue(TenantId tenantId, QueueId queueId) {
        String sql = "SELECT queue_id, tenant_id, code, display_name, profile, ordering_policy, status "
                + "FROM queue_definition WHERE tenant_id = ? AND queue_id = ?";
        try (Connection connection = dataSource.getConnection()) {
            setTenant(connection, tenantId);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, tenantId.value());
                statement.setObject(2, queueId.value());
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(mapQueue(rs));
                }
            }
        } catch (SQLException e) {
            throw persistenceFailure("find queue", e);
        }
    }

    @Override
    @Transactional
    public Optional<QueueEntry> findEntry(TenantId tenantId, QueueId queueId, QueueEntryId entryId) {
        String sql = "SELECT tenant_id, queue_id, entry_id, profile, source_capability, "
                + "source_resource_type, source_resource_id, priority, status, entered_at, updated_at, "
                + "called_at, serving_started_at, completed_at, call_count FROM queue_entry "
                + "WHERE tenant_id = ? AND queue_id = ? AND entry_id = ?";
        try (Connection connection = dataSource.getConnection()) {
            setTenant(connection, tenantId);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, tenantId.value());
                statement.setObject(2, queueId.value());
                statement.setObject(3, entryId.value());
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(mapEntry(rs));
                }
            }
        } catch (SQLException e) {
            throw persistenceFailure("find queue entry", e);
        }
    }

    @Override
    @Transactional
    public List<QueueEntry> findWaiting(TenantId tenantId, QueueId queueId) {
        String sql = "SELECT tenant_id, queue_id, entry_id, profile, source_capability, "
                + "source_resource_type, source_resource_id, priority, status, entered_at, updated_at, "
                + "called_at, serving_started_at, completed_at, call_count FROM queue_entry "
                + "WHERE tenant_id = ? AND queue_id = ? AND status = 'WAITING' "
                + "ORDER BY entered_at, entry_id";
        try (Connection connection = dataSource.getConnection()) {
            setTenant(connection, tenantId);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, tenantId.value());
                statement.setObject(2, queueId.value());
                try (ResultSet rs = statement.executeQuery()) {
                    List<QueueEntry> entries = new ArrayList<>();
                    while (rs.next()) entries.add(mapEntry(rs));
                    return List.copyOf(entries);
                }
            }
        } catch (SQLException e) {
            throw persistenceFailure("find waiting queue entries", e);
        }
    }

    @Override
    @Transactional
    public void saveQueue(QueueDefinition queue) {
        String sql = "INSERT INTO queue_definition "
                + "(tenant_id, queue_id, code, display_name, profile, ordering_policy, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (tenant_id, queue_id) DO UPDATE SET "
                + "display_name = EXCLUDED.display_name, ordering_policy = EXCLUDED.ordering_policy, "
                + "status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP, "
                + "version = queue_definition.version + 1";
        try (Connection connection = dataSource.getConnection()) {
            setTenant(connection, queue.tenantId());
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, queue.tenantId().value());
                statement.setObject(2, queue.id().value());
                statement.setString(3, queue.code());
                statement.setString(4, queue.name());
                statement.setString(5, queue.profile().name());
                statement.setString(6, queue.orderingPolicy().name());
                statement.setString(7, queue.status().name());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw persistenceFailure("save queue", e);
        }
    }

    @Override
    @Transactional
    public void saveEntry(QueueEntry entry) {
        String sql = "INSERT INTO queue_entry (tenant_id, queue_id, entry_id, profile, "
                + "source_capability, source_resource_type, source_resource_id, priority, status, "
                + "entered_at, updated_at, called_at, serving_started_at, completed_at, call_count) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (tenant_id, queue_id, entry_id) DO UPDATE SET "
                + "status = EXCLUDED.status, updated_at = EXCLUDED.updated_at, called_at = EXCLUDED.called_at, "
                + "serving_started_at = EXCLUDED.serving_started_at, completed_at = EXCLUDED.completed_at, "
                + "call_count = EXCLUDED.call_count, version = queue_entry.version + 1 "
                + "WHERE queue_entry.version = EXCLUDED.version";
        // Optimistic updates use the persisted row version in the dedicated update path below.
        try (Connection connection = dataSource.getConnection()) {
            setTenant(connection, entry.tenantId());
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, entry.tenantId().value());
                statement.setObject(2, entry.queueId().value());
                statement.setObject(3, entry.id().value());
                statement.setString(4, entry.profile().name());
                statement.setString(5, entry.source().capability());
                statement.setString(6, entry.source().resourceType());
                statement.setString(7, entry.source().resourceId());
                statement.setInt(8, entry.priority());
                statement.setString(9, entry.status().name());
                statement.setTimestamp(10, Timestamp.from(entry.enteredAt()));
                statement.setTimestamp(11, Timestamp.from(entry.updatedAt()));
                setInstant(statement, 12, entry.calledAt());
                setInstant(statement, 13, entry.servingStartedAt());
                setInstant(statement, 14, entry.completedAt());
                statement.setInt(15, entry.callCount());
                int changed = statement.executeUpdate();
                if (changed == 0) throw new IllegalStateException("Queue entry changed concurrently or identity conflicts");
            }
        } catch (SQLException e) {
            throw persistenceFailure("save queue entry", e);
        }
    }

    @Override
    @Transactional
    public QueueEntry claimNext(TenantId tenantId, QueueId queueId, ClaimMode mode,
                                String actorId, Instant now) {
        String ordering = "qd.ordering_policy = 'PRIORITY_THEN_ARRIVAL' "
                + "THEN e.priority DESC, e.entered_at, e.entry_id ELSE e.entered_at, e.entry_id";
        String sql = "WITH candidate AS (SELECT e.tenant_id, e.queue_id, e.entry_id, e.profile "
                + "FROM queue_entry e JOIN queue_definition qd "
                + "ON qd.tenant_id = e.tenant_id AND qd.queue_id = e.queue_id "
                + "WHERE e.tenant_id = ? AND e.queue_id = ? AND e.status = 'WAITING' "
                + "AND qd.status = 'ACTIVE' AND ((? = 'CALL_SERVICE_ENTRY' AND e.profile = 'WAITING_SERVICE') "
                + "OR (? = 'OFFER_DISPATCH_ENTRY' AND e.profile = 'DISPATCH')) "
                + "ORDER BY CASE WHEN qd.ordering_policy = 'PRIORITY_THEN_ARRIVAL' THEN e.priority END DESC, "
                + "e.entered_at, e.entry_id FOR UPDATE OF e SKIP LOCKED LIMIT 1) "
                + "UPDATE queue_entry e SET status = CASE WHEN ? = 'CALL_SERVICE_ENTRY' THEN 'CALLED' ELSE 'OFFERED' END, "
                + "called_at = CASE WHEN ? = 'CALL_SERVICE_ENTRY' THEN ? ELSE e.called_at END, "
                + "call_count = CASE WHEN ? = 'CALL_SERVICE_ENTRY' THEN e.call_count + 1 ELSE e.call_count END, "
                + "updated_at = ?, version = e.version + 1 FROM candidate c "
                + "WHERE e.tenant_id = c.tenant_id AND e.queue_id = c.queue_id AND e.entry_id = c.entry_id "
                + "RETURNING e.tenant_id, e.queue_id, e.entry_id, e.profile, e.source_capability, "
                + "e.source_resource_type, e.source_resource_id, e.priority, e.status, e.entered_at, "
                + "e.updated_at, e.called_at, e.serving_started_at, e.completed_at, e.call_count";
        try (Connection connection = dataSource.getConnection()) {
            setTenant(connection, tenantId);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int i = 1;
                statement.setObject(i++, tenantId.value());
                statement.setObject(i++, queueId.value());
                statement.setString(i++, mode.name());
                statement.setString(i++, mode.name());
                statement.setString(i++, mode.name());
                statement.setTimestamp(i++, Timestamp.from(now));
                statement.setString(i++, mode.name());
                statement.setString(i++, mode.name());
                statement.setTimestamp(i++, Timestamp.from(now));
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) throw new IllegalStateException("No eligible queue entry is available");
                    return mapEntry(rs);
                }
            }
        } catch (SQLException e) {
            throw persistenceFailure("claim next queue entry", e);
        }
    }

    private static void setTenant(Connection connection, TenantId tenantId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('numou.tenant_id', ?, true)")) {
            statement.setString(1, tenantId.value().toString());
            statement.execute();
        }
    }

    private static QueueDefinition mapQueue(ResultSet rs) throws SQLException {
        return QueueDefinition.reconstitute(QueueId.of(rs.getObject("queue_id", UUID.class)),
                TenantId.of(rs.getObject("tenant_id", UUID.class)), rs.getString("code"),
                rs.getString("display_name"), QueueProfile.valueOf(rs.getString("profile")),
                QueueOrderingPolicy.valueOf(rs.getString("ordering_policy")),
                QueueStatus.valueOf(rs.getString("status")));
    }

    private static QueueEntry mapEntry(ResultSet rs) throws SQLException {
        return QueueEntry.reconstitute(QueueEntryId.of(rs.getObject("entry_id", UUID.class)),
                TenantId.of(rs.getObject("tenant_id", UUID.class)),
                QueueId.of(rs.getObject("queue_id", UUID.class)),
                QueueProfile.valueOf(rs.getString("profile")),
                new QueueSourceReference(rs.getString("source_capability"),
                        rs.getString("source_resource_type"), rs.getString("source_resource_id")),
                rs.getInt("priority"), instant(rs, "entered_at"), instant(rs, "updated_at"),
                QueueEntryStatus.valueOf(rs.getString("status")), instant(rs, "called_at"),
                instant(rs, "serving_started_at"), instant(rs, "completed_at"), rs.getInt("call_count"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static void setInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) statement.setTimestamp(index, null);
        else statement.setTimestamp(index, Timestamp.from(value));
    }

    private static IllegalStateException persistenceFailure(String operation, SQLException cause) {
        return new IllegalStateException("Unable to " + operation + " in Queue Management PostgreSQL adapter", cause);
    }
}
