package tech.kayys.numou.planning.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.planning.application.port.ActivityProgressRepository;
import tech.kayys.numou.planning.domain.ActivityId;
import tech.kayys.numou.planning.domain.ActivityProgressObservation;
import tech.kayys.numou.planning.domain.PlanId;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL adapter for durable, append-only activity progress observations. */
@ApplicationScoped
public class JdbcActivityProgressRepository implements ActivityProgressRepository {
    private final DataSource dataSource;

    @Inject
    public JdbcActivityProgressRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean append(PlanId planId, ActivityProgressObservation observation) {
        String insert = """
                INSERT INTO schedule_activity_progress_observation
                    (plan_id, observation_id, activity_id, as_of_date, percent_complete,
                     recorded_at, recorded_by, idempotency_key, source_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (plan_id, idempotency_key) DO NOTHING
                RETURNING observation_id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setObject(1, planId.value());
            statement.setObject(2, observation.id());
            statement.setObject(3, observation.activityId().value());
            statement.setObject(4, observation.asOfDate());
            statement.setBigDecimal(5, observation.percentComplete());
            statement.setTimestamp(6, Timestamp.from(observation.recordedAt()));
            statement.setString(7, observation.recordedBy());
            statement.setString(8, observation.idempotencyKey());
            statement.setString(9, observation.sourceReference());
            try (ResultSet inserted = statement.executeQuery()) {
                if (inserted.next()) return true;
            }
            ActivityProgressObservation existing = findByIdempotencyKey(connection, planId, observation.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException(
                            "Progress idempotency conflict occurred but the original observation was not found"));
            if (sameBusinessPayload(existing, observation)) return false;
            throw new IllegalArgumentException("Idempotency key was already used for different progress data");
        } catch (SQLException e) {
            throw persistenceFailure("append activity progress observation", e);
        }
    }

    @Override
    public List<ActivityProgressObservation> findHistory(PlanId planId, ActivityId activityId) {
        String sql = """
                SELECT observation_id, activity_id, as_of_date, percent_complete,
                       recorded_at, recorded_by, idempotency_key, source_reference
                FROM schedule_activity_progress_observation
                WHERE plan_id = ? AND activity_id = ?
                ORDER BY as_of_date, recorded_at, observation_id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, planId.value());
            statement.setObject(2, activityId.value());
            try (ResultSet rows = statement.executeQuery()) {
                List<ActivityProgressObservation> observations = new ArrayList<>();
                while (rows.next()) observations.add(map(rows));
                return List.copyOf(observations);
            }
        } catch (SQLException e) {
            throw persistenceFailure("load activity progress history", e);
        }
    }

    @Override
    public Optional<ActivityProgressObservation> findLatestAsOf(
            PlanId planId, ActivityId activityId, LocalDate asOfDate) {
        String sql = """
                SELECT observation_id, activity_id, as_of_date, percent_complete,
                       recorded_at, recorded_by, idempotency_key, source_reference
                FROM schedule_activity_progress_observation
                WHERE plan_id = ? AND activity_id = ? AND as_of_date <= ?
                ORDER BY as_of_date DESC, recorded_at DESC, observation_id DESC
                LIMIT 1
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, planId.value());
            statement.setObject(2, activityId.value());
            statement.setObject(3, asOfDate);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw persistenceFailure("load latest activity progress observation", e);
        }
    }

    private Optional<ActivityProgressObservation> findByIdempotencyKey(
            Connection connection, PlanId planId, String key) throws SQLException {
        String sql = """
                SELECT observation_id, activity_id, as_of_date, percent_complete,
                       recorded_at, recorded_by, idempotency_key, source_reference
                FROM schedule_activity_progress_observation
                WHERE plan_id = ? AND idempotency_key = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, planId.value());
            statement.setString(2, key);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    private ActivityProgressObservation map(ResultSet row) throws SQLException {
        return new ActivityProgressObservation(
                (UUID) row.getObject("observation_id"),
                new ActivityId((UUID) row.getObject("activity_id")),
                row.getObject("as_of_date", LocalDate.class),
                row.getBigDecimal("percent_complete"),
                row.getTimestamp("recorded_at").toInstant(),
                row.getString("recorded_by"),
                row.getString("idempotency_key"),
                row.getString("source_reference"));
    }

    private boolean sameBusinessPayload(ActivityProgressObservation left,
                                        ActivityProgressObservation right) {
        return left.activityId().equals(right.activityId())
                && left.asOfDate().equals(right.asOfDate())
                && left.percentComplete().compareTo(right.percentComplete()) == 0
                && left.recordedBy().equals(right.recordedBy())
                && java.util.Objects.equals(left.sourceReference(), right.sourceReference());
    }

    private IllegalStateException persistenceFailure(String action, SQLException e) {
        return new IllegalStateException("Unable to " + action + " (SQLState=" + e.getSQLState() + ")", e);
    }
}
