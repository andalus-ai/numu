package tech.kayys.numou.planning.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.planning.application.port.ScheduleChangeExecutionStore;
import tech.kayys.numou.planning.domain.*;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL transaction adapter for approved schedule changes. */
@ApplicationScoped
public class JdbcScheduleChangeExecutionStore implements ScheduleChangeExecutionStore {
    private final DataSource dataSource;

    @Inject
    public JdbcScheduleChangeExecutionStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<ScheduleChangeExecution> findByExecutionId(UUID id) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM schedule_change_execution WHERE execution_id=?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(receipt(rs)) : Optional.empty();
            }
        } catch (SQLException e) { throw failure("load execution receipt", e); }
    }

    @Override
    public boolean matchesReplay(UUID id, ScheduleChangeRequest request, SchedulePlan target, UUID actor) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT payload_fingerprint FROM schedule_change_execution WHERE execution_id=?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && fingerprint(request, target, actor).equals(rs.getString(1));
            }
        } catch (SQLException e) { throw failure("verify execution replay", e); }
    }

    @Override
    public ScheduleChangeExecution applyAtomically(ScheduleChangeRequest request, SchedulePlan target,
                                                    ScheduleChangeExecution applying) {
        String fingerprint = fingerprint(request, target, applying.initiatedBy());
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                ScheduleChangeExecution prior = findReceipt(c, applying.executionId());
                if (prior != null) {
                    if (!fingerprint.equals(findFingerprint(c, applying.executionId())))
                        throw new IllegalArgumentException("Execution id was reused with a different payload");
                    c.commit();
                    return prior;
                }

                long revision;
                try (PreparedStatement lock = c.prepareStatement(
                        "SELECT revision FROM schedule_plan WHERE plan_id=? FOR UPDATE")) {
                    lock.setObject(1, request.planId().value());
                    try (ResultSet rs = lock.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Schedule plan was not found");
                        revision = rs.getLong(1);
                    }
                }

                if (!hasScope(c, request)) {
                    ScheduleChangeExecution result = conflict(applying,
                            "Plan is not registered to the requested tenant and structure");
                    insertReceipt(c, result, fingerprint);
                    c.commit();
                    return result;
                }
                if (revision != request.baseRevision()) {
                    ScheduleChangeExecution result = conflict(applying,
                            "Live plan revision changed after approval; no schedule changes were applied");
                    insertReceipt(c, result, fingerprint);
                    c.commit();
                    return result;
                }
                if (target.revision() != request.targetRevision()
                        || request.targetRevision() != request.baseRevision() + 1)
                    throw new IllegalArgumentException("Target revision must equal base revision plus one");

                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE schedule_plan SET code=?, name=?, status=?, revision=?, updated_at=CURRENT_TIMESTAMP "
                                + "WHERE plan_id=? AND revision=?")) {
                    ps.setString(1, target.code());
                    ps.setString(2, target.name());
                    ps.setString(3, target.status().name());
                    ps.setLong(4, request.targetRevision());
                    ps.setObject(5, request.planId().value());
                    ps.setLong(6, request.baseRevision());
                    if (ps.executeUpdate() != 1) {
                        ScheduleChangeExecution result = conflict(applying, "Plan revision compare-and-set failed");
                        insertReceipt(c, result, fingerprint);
                        c.commit();
                        return result;
                    }
                }

                replaceSchedule(c, target);
                ScheduleChangeExecution result = new ScheduleChangeExecution(
                        applying.executionId(), applying.requestId(), applying.tenantId(), applying.structureId(),
                        applying.planId(), applying.baseRevision(), applying.targetRevision(), applying.initiatedBy(),
                        applying.startedAt(), ScheduleChangeExecution.Status.APPLIED, request.targetRevision(),
                        nowAfter(applying.startedAt()), null);
                insertReceipt(c, result, fingerprint);
                c.commit();
                return result;
            } catch (Exception e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                if (e instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("Unable to apply approved schedule change", e);
            }
        } catch (SQLException e) { throw failure("apply approved schedule change", e); }
    }

    private boolean hasScope(Connection c, ScheduleChangeRequest request) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM schedule_plan_scope WHERE plan_id=? AND tenant_id=? AND structure_id=?")) {
            ps.setObject(1, request.planId().value());
            ps.setObject(2, request.tenantId());
            ps.setObject(3, request.structureId());
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private void replaceSchedule(Connection c, SchedulePlan plan) throws SQLException {
        UUID planId = plan.id().value();
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM schedule_dependency WHERE plan_id=?")) {
            ps.setObject(1, planId);
            ps.executeUpdate();
        }
        java.util.Set<UUID> retained = new java.util.HashSet<>();
        for (ScheduleActivity a : plan.activities()) {
            retained.add(a.id().value());
            int count;
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE schedule_activity SET code=?,name=?,planned_start=?,planned_finish=?,work_item_type=?,"
                            + "work_item_id=?,position=? WHERE plan_id=? AND activity_id=?")) {
                bindActivity(ps, a, 1);
                ps.setObject(8, planId);
                ps.setObject(9, a.id().value());
                count = ps.executeUpdate();
            }
            if (count == 0) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO schedule_activity(plan_id,activity_id,code,name,planned_start,planned_finish,"
                                + "work_item_type,work_item_id,position) VALUES (?,?,?,?,?,?,?,?,?)")) {
                    ps.setObject(1, planId);
                    ps.setObject(2, a.id().value());
                    bindActivity(ps, a, 3);
                    ps.executeUpdate();
                }
            }
        }
        try (PreparedStatement query = c.prepareStatement("SELECT activity_id FROM schedule_activity WHERE plan_id=?");
             PreparedStatement delete = c.prepareStatement(
                     "DELETE FROM schedule_activity WHERE plan_id=? AND activity_id=?")) {
            query.setObject(1, planId);
            try (ResultSet rs = query.executeQuery()) {
                while (rs.next()) {
                    UUID id = (UUID) rs.getObject(1);
                    if (!retained.contains(id)) {
                        delete.setObject(1, planId);
                        delete.setObject(2, id);
                        delete.addBatch();
                    }
                }
            }
            delete.executeBatch();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO schedule_dependency(plan_id,predecessor_id,successor_id) VALUES (?,?,?)")) {
            for (ActivityDependency d : plan.dependencies()) {
                ps.setObject(1, planId);
                ps.setObject(2, d.predecessor().value());
                ps.setObject(3, d.successor().value());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void bindActivity(PreparedStatement ps, ScheduleActivity a, int i) throws SQLException {
        WorkItemReference w = a.workItem();
        ps.setString(i, a.code());
        ps.setString(i + 1, a.name());
        ps.setObject(i + 2, a.plannedStart());
        ps.setObject(i + 3, a.plannedFinish());
        ps.setString(i + 4, w == null ? null : w.type());
        ps.setString(i + 5, w == null ? null : w.id());
        ps.setInt(i + 6, a.position());
    }

    private ScheduleChangeExecution findReceipt(Connection c, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM schedule_change_execution WHERE execution_id=?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? receipt(rs) : null; }
        }
    }

    private String findFingerprint(Connection c, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT payload_fingerprint FROM schedule_change_execution WHERE execution_id=?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        }
    }

    private void insertReceipt(Connection c, ScheduleChangeExecution r, String fingerprint) throws SQLException {
        String sql = "INSERT INTO schedule_change_execution(execution_id,request_id,tenant_id,structure_id,plan_id,"
                + "base_revision,target_revision,initiated_by,started_at,status,resulting_revision,completed_at,"
                + "failure_reason,payload_fingerprint) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, r.executionId()); ps.setObject(2, r.requestId()); ps.setObject(3, r.tenantId());
            ps.setObject(4, r.structureId()); ps.setObject(5, r.planId().value());
            ps.setLong(6, r.baseRevision()); ps.setLong(7, r.targetRevision()); ps.setObject(8, r.initiatedBy());
            ps.setTimestamp(9, Timestamp.from(r.startedAt())); ps.setString(10, r.status().name());
            if (r.resultingRevision() == null) ps.setNull(11, Types.BIGINT); else ps.setLong(11, r.resultingRevision());
            ps.setTimestamp(12, Timestamp.from(r.completedAt())); ps.setString(13, r.failureReason());
            ps.setString(14, fingerprint); ps.executeUpdate();
        }
    }

    private ScheduleChangeExecution receipt(ResultSet rs) throws SQLException {
        long raw = rs.getLong("resulting_revision");
        Long resulting = rs.wasNull() ? null : raw;
        Timestamp completed = rs.getTimestamp("completed_at");
        return new ScheduleChangeExecution((UUID) rs.getObject("execution_id"), (UUID) rs.getObject("request_id"),
                (UUID) rs.getObject("tenant_id"), (UUID) rs.getObject("structure_id"),
                new PlanId((UUID) rs.getObject("plan_id")), rs.getLong("base_revision"),
                rs.getLong("target_revision"), (UUID) rs.getObject("initiated_by"),
                rs.getTimestamp("started_at").toInstant(),
                ScheduleChangeExecution.Status.valueOf(rs.getString("status")), resulting,
                completed == null ? null : completed.toInstant(), rs.getString("failure_reason"));
    }

    private ScheduleChangeExecution conflict(ScheduleChangeExecution r, String reason) {
        return new ScheduleChangeExecution(r.executionId(), r.requestId(), r.tenantId(), r.structureId(), r.planId(),
                r.baseRevision(), r.targetRevision(), r.initiatedBy(), r.startedAt(),
                ScheduleChangeExecution.Status.CONFLICT, null, nowAfter(r.startedAt()), reason);
    }

    private Instant nowAfter(Instant start) {
        Instant now = Instant.now();
        return now.isBefore(start) ? start : now;
    }

    private String fingerprint(ScheduleChangeRequest request, SchedulePlan plan, UUID actor) {
        StringBuilder b = new StringBuilder().append(request).append('|').append(actor).append('|')
                .append(plan.id()).append('|').append(plan.code()).append('|').append(plan.name()).append('|')
                .append(plan.status()).append('|').append(plan.revision()).append('|');
        plan.activities().stream().sorted(java.util.Comparator.comparing(a -> a.id().value().toString()))
                .forEach(a -> b.append(a).append(';'));
        plan.dependencies().stream().sorted(java.util.Comparator
                .comparing((ActivityDependency d) -> d.predecessor().value().toString())
                .thenComparing(d -> d.successor().value().toString())).forEach(d -> b.append(d).append(';'));
        plan.baselines().forEach(baseline -> b.append(baseline).append(';'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(b.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private IllegalStateException failure(String action, SQLException cause) {
        return new IllegalStateException("Failed to " + action + ": " + cause.getMessage(), cause);
    }
}
