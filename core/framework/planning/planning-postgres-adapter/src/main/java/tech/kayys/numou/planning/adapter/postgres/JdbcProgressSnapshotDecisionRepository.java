package tech.kayys.numou.planning.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.planning.application.port.ProgressSnapshotDecisionRepository;
import tech.kayys.numou.planning.domain.ProgressSnapshotDecision;
import tech.kayys.numou.planning.domain.ProgressSnapshotDecision.Outcome;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL append-only audit adapter for snapshot approval/rejection events. */
@ApplicationScoped
public class JdbcProgressSnapshotDecisionRepository implements ProgressSnapshotDecisionRepository {
    private final DataSource dataSource;

    @Inject
    public JdbcProgressSnapshotDecisionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void append(ProgressSnapshotDecision decision) {
        if (decision == null) throw new IllegalArgumentException("Decision cannot be null");
        String sql = "INSERT INTO work_breakdown_progress_snapshot_decision "
                + "(tenant_id, decision_id, snapshot_id, actor_id, outcome, reason, decided_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, decision.tenantId());
            statement.setObject(2, decision.decisionId());
            statement.setObject(3, decision.snapshotId());
            statement.setObject(4, decision.actorId());
            statement.setString(5, decision.outcome().name());
            statement.setString(6, decision.reason());
            statement.setObject(7, OffsetDateTime.ofInstant(decision.decidedAt(), ZoneOffset.UTC));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to append progress snapshot decision", e);
        }
    }

    @Override
    public List<ProgressSnapshotDecision> findHistory(UUID tenantId, UUID snapshotId) {
        requireIds(tenantId, snapshotId);
        return query(tenantId, snapshotId, false);
    }

    @Override
    public Optional<ProgressSnapshotDecision> findLatest(UUID tenantId, UUID snapshotId) {
        requireIds(tenantId, snapshotId);
        List<ProgressSnapshotDecision> decisions = query(tenantId, snapshotId, true);
        return decisions.stream().findFirst();
    }

    private List<ProgressSnapshotDecision> query(UUID tenantId, UUID snapshotId, boolean latestOnly) {
        String sql = "SELECT decision_id, tenant_id, snapshot_id, actor_id, outcome, reason, decided_at "
                + "FROM work_breakdown_progress_snapshot_decision WHERE tenant_id = ? AND snapshot_id = ? "
                + "ORDER BY decided_at DESC, decision_id DESC" + (latestOnly ? " LIMIT 1" : "");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, snapshotId);
            List<ProgressSnapshotDecision> result = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    OffsetDateTime timestamp = rows.getObject("decided_at", OffsetDateTime.class);
                    result.add(new ProgressSnapshotDecision(
                            rows.getObject("decision_id", UUID.class),
                            rows.getObject("tenant_id", UUID.class),
                            rows.getObject("snapshot_id", UUID.class),
                            rows.getObject("actor_id", UUID.class),
                            Outcome.valueOf(rows.getString("outcome")),
                            rows.getString("reason"),
                            timestamp.toInstant()));
                }
            }
            return List.copyOf(result);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to query progress snapshot decision history", e);
        }
    }

    private static void requireIds(UUID tenantId, UUID snapshotId) {
        if (tenantId == null || snapshotId == null)
            throw new IllegalArgumentException("Tenant and snapshot identifiers are required");
    }
}
