package tech.kayys.numou.planning.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.planning.application.port.PlanRepository;
import tech.kayys.numou.planning.domain.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.sql.Timestamp;

/**
 * JDBC adapter with optimistic revision checks and one transaction per aggregate save.
 * Baseline rows are inserted only; PostgreSQL triggers prohibit updating or deleting
 * baseline headers and snapshot rows after they have been written.
 */
@ApplicationScoped
public class JdbcPlanRepository implements PlanRepository {
    private final DataSource dataSource;

    @Inject
    public JdbcPlanRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<SchedulePlan> findById(PlanId id) {
        String sql = "SELECT code, name, status, revision FROM schedule_plan WHERE plan_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id.value());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return Optional.empty();
                UUID uuid = id.value();
                String code = row.getString("code");
                String name = row.getString("name");
                PlanStatus status = PlanStatus.valueOf(row.getString("status"));
                long revision = row.getLong("revision");
                List<ScheduleActivity> activities = readActivities(connection, uuid, false, null);
                List<ActivityDependency> dependencies = readDependencies(connection, uuid, false, null);
                List<ScheduleBaseline> baselines = readBaselines(connection, uuid);
                return Optional.of(SchedulePlan.reconstitute(id, code, name, status, revision,
                        activities, dependencies, baselines));
            }
        } catch (SQLException e) {
            throw persistenceFailure("load schedule plan", e);
        }
    }

    @Override
    public boolean existsByCode(String code) {
        String sql = "SELECT EXISTS (SELECT 1 FROM schedule_plan WHERE lower(code) = lower(?))";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getBoolean(1);
            }
        } catch (SQLException e) {
            throw persistenceFailure("check schedule plan code", e);
        }
    }

    @Override
    public void save(SchedulePlan plan) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long nextRevision = persistPlanHeader(connection, plan);
                replaceWorkingSchedule(connection, plan);
                appendBaselines(connection, plan);
                connection.commit();
                plan.markPersisted(nextRevision);
            } catch (Exception failure) {
                rollbackQuietly(connection, failure);
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("Unable to persist schedule plan", failure);
            }
        } catch (SQLException e) {
            throw persistenceFailure("save schedule plan", e);
        }
    }

    private long persistPlanHeader(Connection connection, SchedulePlan plan) throws SQLException {
        try (PreparedStatement lookup = connection.prepareStatement(
                "SELECT revision FROM schedule_plan WHERE plan_id = ?")) {
            lookup.setObject(1, plan.id().value());
            try (ResultSet row = lookup.executeQuery()) {
                if (!row.next()) {
                    if (plan.revision() != 0) {
                        throw new OptimisticPlanConflictException("A new plan must start at revision zero");
                    }
                    String insert = "INSERT INTO schedule_plan(plan_id, code, name, status, revision) VALUES (?, ?, ?, ?, 1)";
                    try (PreparedStatement statement = connection.prepareStatement(insert)) {
                        statement.setObject(1, plan.id().value());
                        statement.setString(2, plan.code());
                        statement.setString(3, plan.name());
                        statement.setString(4, plan.status().name());
                        statement.executeUpdate();
                    }
                    return 1;
                }
                long storedRevision = row.getLong(1);
                if (storedRevision != plan.revision()) {
                    throw new OptimisticPlanConflictException("Schedule plan changed concurrently; reload before saving");
                }
            }
        }
        String update = "UPDATE schedule_plan SET name = ?, status = ?, revision = revision + 1, "
                + "updated_at = CURRENT_TIMESTAMP WHERE plan_id = ? AND revision = ?";
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            statement.setString(1, plan.name());
            statement.setString(2, plan.status().name());
            statement.setObject(3, plan.id().value());
            statement.setLong(4, plan.revision());
            if (statement.executeUpdate() != 1) {
                throw new OptimisticPlanConflictException("Schedule plan changed concurrently; reload before saving");
            }
        }
        return plan.revision() + 1;
    }

    private void replaceWorkingSchedule(Connection connection, SchedulePlan plan) throws SQLException {
        try (PreparedStatement deleteDependencies = connection.prepareStatement(
                    "DELETE FROM schedule_dependency WHERE plan_id = ?");
             PreparedStatement deleteActivities = connection.prepareStatement(
                    "DELETE FROM schedule_activity WHERE plan_id = ?")) {
            deleteDependencies.setObject(1, plan.id().value());
            deleteDependencies.executeUpdate();
            deleteActivities.setObject(1, plan.id().value());
            deleteActivities.executeUpdate();
        }
        for (ScheduleActivity activity : plan.activities()) insertActivity(connection,
                "schedule_activity", plan.id().value(), null, activity);
        for (ActivityDependency dependency : plan.dependencies()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO schedule_dependency(plan_id, predecessor_id, successor_id) VALUES (?, ?, ?)")) {
                statement.setObject(1, plan.id().value());
                statement.setObject(2, dependency.predecessor().value());
                statement.setObject(3, dependency.successor().value());
                statement.executeUpdate();
            }
        }
    }

    private void appendBaselines(Connection connection, SchedulePlan plan) throws SQLException {
        List<BaselineIdentity> stored = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT baseline_id, sequence FROM schedule_baseline WHERE plan_id = ? ORDER BY sequence")) {
            statement.setObject(1, plan.id().value());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) stored.add(new BaselineIdentity(
                        (UUID) rows.getObject("baseline_id"), rows.getInt("sequence")));
            }
        }
        if (stored.size() > plan.baselines().size()) {
            throw new OptimisticPlanConflictException("Persisted baseline history is ahead of this plan");
        }
        for (int i = 0; i < stored.size(); i++) {
            ScheduleBaseline candidate = plan.baselines().get(i);
            BaselineIdentity existing = stored.get(i);
            if (candidate.sequence() != existing.sequence() || !candidate.id().equals(existing.id())) {
                throw new OptimisticPlanConflictException("Existing baseline identity or sequence was altered");
            }
        }
        for (int i = stored.size(); i < plan.baselines().size(); i++) {
            ScheduleBaseline baseline = plan.baselines().get(i);
            if (baseline.sequence() != i + 1) {
                throw new IllegalArgumentException("Baseline sequence must be contiguous from one");
            }
            insertBaseline(connection, plan.id().value(), baseline);
        }
    }

    private void insertBaseline(Connection connection, UUID planId, ScheduleBaseline baseline) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO schedule_baseline(plan_id, baseline_id, sequence, name, created_at, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, planId);
            statement.setObject(2, baseline.id());
            statement.setInt(3, baseline.sequence());
            statement.setString(4, baseline.name());
            statement.setTimestamp(5, Timestamp.from(baseline.createdAt()));
            statement.setString(6, baseline.createdBy());
            statement.executeUpdate();
        }
        for (ScheduleActivity activity : baseline.activities()) {
            insertActivity(connection, "schedule_baseline_activity", planId, baseline.id(), activity);
        }
        for (ActivityDependency dependency : baseline.dependencies()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO schedule_baseline_dependency(plan_id, baseline_id, predecessor_id, successor_id) "
                            + "VALUES (?, ?, ?, ?)")) {
                statement.setObject(1, planId);
                statement.setObject(2, baseline.id());
                statement.setObject(3, dependency.predecessor().value());
                statement.setObject(4, dependency.successor().value());
                statement.executeUpdate();
            }
        }
    }

    private void insertActivity(Connection connection, String table, UUID planId, UUID baselineId,
                                ScheduleActivity activity) throws SQLException {
        String columns = baselineId == null
                ? "plan_id, activity_id, code, name, planned_start, planned_finish, work_item_type, work_item_id, position"
                : "plan_id, baseline_id, activity_id, code, name, planned_start, planned_finish, work_item_type, work_item_id, position";
        String values = baselineId == null ? "?, ?, ?, ?, ?, ?, ?, ?, ?" : "?, ?, ?, ?, ?, ?, ?, ?, ?, ?";
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + table + "(" + columns + ") VALUES (" + values + ")")) {
            int i = 1;
            statement.setObject(i++, planId);
            if (baselineId != null) statement.setObject(i++, baselineId);
            statement.setObject(i++, activity.id().value());
            statement.setString(i++, activity.code());
            statement.setString(i++, activity.name());
            statement.setObject(i++, activity.plannedStart());
            statement.setObject(i++, activity.plannedFinish());
            WorkItemReference workItem = activity.workItem();
            statement.setString(i++, workItem == null ? null : workItem.type());
            statement.setString(i++, workItem == null ? null : workItem.id());
            statement.setInt(i, activity.position());
            statement.executeUpdate();
        }
    }

    private List<ScheduleActivity> readActivities(Connection connection, UUID planId, boolean baseline, UUID baselineId)
            throws SQLException {
        String table = baseline ? "schedule_baseline_activity" : "schedule_activity";
        String where = baseline ? "plan_id = ? AND baseline_id = ?" : "plan_id = ?";
        String sql = "SELECT activity_id, code, name, planned_start, planned_finish, work_item_type, work_item_id, position "
                + "FROM " + table + " WHERE " + where + " ORDER BY position, code";
        List<ScheduleActivity> activities = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, planId);
            if (baseline) statement.setObject(2, baselineId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String type = rows.getString("work_item_type");
                    String workId = rows.getString("work_item_id");
                    WorkItemReference reference = type == null ? null : new WorkItemReference(type, workId);
                    activities.add(new ScheduleActivity(new ActivityId((UUID) rows.getObject("activity_id")),
                            rows.getString("code"), rows.getString("name"),
                            rows.getObject("planned_start", LocalDate.class),
                            rows.getObject("planned_finish", LocalDate.class), reference, rows.getInt("position")));
                }
            }
        }
        return activities;
    }

    private List<ActivityDependency> readDependencies(Connection connection, UUID planId, boolean baseline, UUID baselineId)
            throws SQLException {
        String table = baseline ? "schedule_baseline_dependency" : "schedule_dependency";
        String where = baseline ? "plan_id = ? AND baseline_id = ?" : "plan_id = ?";
        String sql = "SELECT predecessor_id, successor_id FROM " + table + " WHERE " + where;
        List<ActivityDependency> dependencies = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, planId);
            if (baseline) statement.setObject(2, baselineId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) dependencies.add(new ActivityDependency(
                        new ActivityId((UUID) rows.getObject("predecessor_id")),
                        new ActivityId((UUID) rows.getObject("successor_id"))));
            }
        }
        return dependencies;
    }

    private List<ScheduleBaseline> readBaselines(Connection connection, UUID planId) throws SQLException {
        List<ScheduleBaseline> baselines = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT baseline_id, sequence, name, created_at, created_by FROM schedule_baseline "
                        + "WHERE plan_id = ? ORDER BY sequence")) {
            statement.setObject(1, planId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    UUID baselineId = (UUID) rows.getObject("baseline_id");
                    baselines.add(new ScheduleBaseline(baselineId, rows.getInt("sequence"),
                            rows.getString("name"), rows.getTimestamp("created_at").toInstant(),
                            rows.getString("created_by"),
                            readActivities(connection, planId, true, baselineId),
                            readDependencies(connection, planId, true, baselineId)));
                }
            }
        }
        return baselines;
    }

    private void rollbackQuietly(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private IllegalStateException persistenceFailure(String action, SQLException cause) {
        return new IllegalStateException("Failed to " + action + ": " + cause.getMessage(), cause);
    }

    private record BaselineIdentity(UUID id, int sequence) {}

    public static final class OptimisticPlanConflictException extends RuntimeException {
        public OptimisticPlanConflictException(String message) {
            super(message);
        }
    }
}
