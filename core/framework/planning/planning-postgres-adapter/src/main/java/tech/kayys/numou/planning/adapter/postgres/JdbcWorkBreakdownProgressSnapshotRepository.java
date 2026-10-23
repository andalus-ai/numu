package tech.kayys.numou.planning.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.planning.application.port.WorkBreakdownProgressSnapshotRepository;
import tech.kayys.numou.planning.domain.WorkBreakdownProgressSnapshot;
import tech.kayys.numou.planning.domain.WorkBreakdownProgressSnapshot.ActivityContribution;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL adapter persisting WBS progress snapshots and their frozen inputs atomically. */
@ApplicationScoped
public class JdbcWorkBreakdownProgressSnapshotRepository implements WorkBreakdownProgressSnapshotRepository {
    private final DataSource dataSource;

    @Inject
    public JdbcWorkBreakdownProgressSnapshotRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void create(WorkBreakdownProgressSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("Snapshot cannot be null");
        String headerSql = "INSERT INTO work_breakdown_progress_snapshot "
                + "(tenant_id, snapshot_id, structure_id, node_id, plan_id, plan_revision, as_of_date, "
                + "weighting_policy_version, completion_percent, total_weight, included_activity_count, recorded_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String detailSql = "INSERT INTO work_breakdown_progress_snapshot_activity "
                + "(tenant_id, snapshot_id, plan_id, activity_id, actual_percent, weight) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(headerSql)) {
                    statement.setObject(1, snapshot.tenantId());
                    statement.setObject(2, snapshot.snapshotId());
                    statement.setObject(3, snapshot.structureId());
                    statement.setObject(4, snapshot.nodeId());
                    statement.setObject(5, snapshot.planId());
                    statement.setLong(6, snapshot.planRevision());
                    statement.setObject(7, snapshot.asOfDate());
                    statement.setString(8, snapshot.weightingPolicyVersion());
                    statement.setBigDecimal(9, snapshot.completionPercent());
                    statement.setBigDecimal(10, snapshot.totalWeight());
                    statement.setInt(11, snapshot.includedActivityCount());
                    statement.setObject(12, OffsetDateTime.ofInstant(snapshot.recordedAt(), ZoneOffset.UTC));
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(detailSql)) {
                    for (ActivityContribution contribution : snapshot.contributions()) {
                        statement.setObject(1, snapshot.tenantId());
                        statement.setObject(2, snapshot.snapshotId());
                        statement.setObject(3, snapshot.planId());
                        statement.setObject(4, contribution.activityId());
                        statement.setBigDecimal(5, contribution.actualPercent());
                        statement.setBigDecimal(6, contribution.weight());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
            } catch (Exception failure) {
                try { connection.rollback(); } catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("Unable to persist WBS progress snapshot", failure);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to create WBS progress snapshot", e);
        }
    }

    @Override
    public Optional<WorkBreakdownProgressSnapshot> findById(UUID tenantId, UUID snapshotId) {
        requireIds(tenantId, snapshotId);
        String sql = baseSelect() + " WHERE s.tenant_id = ? AND s.snapshot_id = ? "
                + "ORDER BY d.activity_id";
        List<WorkBreakdownProgressSnapshot> found = query(sql, statement -> {
            statement.setObject(1, tenantId);
            statement.setObject(2, snapshotId);
        });
        return found.stream().findFirst();
    }

    @Override
    public List<WorkBreakdownProgressSnapshot> findHistory(
            UUID tenantId, UUID structureId, UUID nodeId, LocalDate from, LocalDate through) {
        if (tenantId == null || structureId == null || nodeId == null || from == null || through == null)
            throw new IllegalArgumentException("Tenant, structure, node and date range are required");
        if (from.isAfter(through)) throw new IllegalArgumentException("History start date must not follow end date");
        String sql = baseSelect() + " WHERE s.tenant_id = ? AND s.structure_id = ? AND s.node_id = ? "
                + "AND s.as_of_date BETWEEN ? AND ? ORDER BY s.as_of_date DESC, s.recorded_at DESC, d.activity_id";
        return query(sql, statement -> {
            statement.setObject(1, tenantId);
            statement.setObject(2, structureId);
            statement.setObject(3, nodeId);
            statement.setObject(4, from);
            statement.setObject(5, through);
        });
    }

    private String baseSelect() {
        return "SELECT s.tenant_id, s.snapshot_id, s.structure_id, s.node_id, s.plan_id, s.plan_revision, "
                + "s.as_of_date, s.weighting_policy_version, s.completion_percent, s.total_weight, "
                + "s.included_activity_count, s.recorded_at, d.activity_id, d.actual_percent, d.weight "
                + "FROM work_breakdown_progress_snapshot s "
                + "JOIN work_breakdown_progress_snapshot_activity d "
                + "ON d.tenant_id = s.tenant_id AND d.snapshot_id = s.snapshot_id";
    }

    private List<WorkBreakdownProgressSnapshot> query(String sql, Binder binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            Map<UUID, SnapshotBuilder> builders = new LinkedHashMap<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    UUID id = rows.getObject("snapshot_id", UUID.class);
                    SnapshotBuilder builder = builders.computeIfAbsent(id, ignored -> new SnapshotBuilder(rows));
                    builder.contributions.add(new ActivityContribution(
                            rows.getObject("activity_id", UUID.class),
                            rows.getBigDecimal("actual_percent"), rows.getBigDecimal("weight")));
                }
            }
            return builders.values().stream().map(SnapshotBuilder::build).toList();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to query WBS progress snapshots", e);
        }
    }

    private static void requireIds(UUID tenantId, UUID snapshotId) {
        if (tenantId == null || snapshotId == null) throw new IllegalArgumentException("Tenant and snapshot id are required");
    }

    @FunctionalInterface
    private interface Binder { void bind(PreparedStatement statement) throws SQLException; }

    private static final class SnapshotBuilder {
        private final UUID snapshotId, tenantId, structureId, nodeId, planId;
        private final long planRevision;
        private final LocalDate asOfDate;
        private final String weightingPolicyVersion;
        private final java.math.BigDecimal completionPercent, totalWeight;
        private final int includedActivityCount;
        private final Instant recordedAt;
        private final List<ActivityContribution> contributions = new ArrayList<>();

        private SnapshotBuilder(ResultSet row) {
            try {
                snapshotId = row.getObject("snapshot_id", UUID.class);
                tenantId = row.getObject("tenant_id", UUID.class);
                structureId = row.getObject("structure_id", UUID.class);
                nodeId = row.getObject("node_id", UUID.class);
                planId = row.getObject("plan_id", UUID.class);
                planRevision = row.getLong("plan_revision");
                asOfDate = row.getObject("as_of_date", LocalDate.class);
                weightingPolicyVersion = row.getString("weighting_policy_version");
                completionPercent = row.getBigDecimal("completion_percent");
                totalWeight = row.getBigDecimal("total_weight");
                includedActivityCount = row.getInt("included_activity_count");
                recordedAt = row.getObject("recorded_at", java.time.OffsetDateTime.class).toInstant();
            } catch (SQLException e) {
                throw new IllegalStateException("Unable to read WBS progress snapshot", e);
            }
        }

        private WorkBreakdownProgressSnapshot build() {
            return new WorkBreakdownProgressSnapshot(snapshotId, tenantId, structureId, nodeId, planId,
                    planRevision, asOfDate, weightingPolicyVersion, completionPercent, totalWeight,
                    includedActivityCount, recordedAt, contributions);
        }
    }
}
