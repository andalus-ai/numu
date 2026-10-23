package tech.kayys.numou.planning.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.planning.application.port.ScopeQuantityRepository;
import tech.kayys.numou.planning.domain.QuantityMeasurement;
import tech.kayys.numou.planning.domain.ScopeQuantityDefinition;

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

/** PostgreSQL adapter; every read is explicitly constrained by tenant_id. */
@ApplicationScoped
public class JdbcScopeQuantityRepository implements ScopeQuantityRepository {
    private final DataSource dataSource;

    @Inject
    public JdbcScopeQuantityRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean createDefinition(ScopeQuantityDefinition definition) {
        String sql = """
                INSERT INTO planning_scope_quantity_definition
                    (tenant_id, definition_id, scope_type, scope_id, quantity_code,
                     description, unit_code, planned_quantity, revision)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, definition_id) DO NOTHING
                RETURNING definition_id
                """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, definition.tenantId());
            s.setObject(2, definition.id());
            s.setString(3, definition.scopeType());
            s.setString(4, definition.scopeId());
            s.setString(5, definition.code());
            s.setString(6, definition.description());
            s.setString(7, definition.unitCode());
            s.setBigDecimal(8, definition.plannedQuantity());
            s.setInt(9, definition.revision());
            try (ResultSet rs = s.executeQuery()) {
                if (rs.next()) return true;
            }
            ScopeQuantityDefinition existing = findDefinition(definition.tenantId(), definition.id())
                    .orElseThrow(() -> new IllegalStateException("Definition conflict but existing row was not found"));
            if (existing.equals(definition)) return false;
            throw new IllegalArgumentException("Definition ID already exists with different content");
        } catch (SQLException e) {
            throw persistenceFailure("create scope quantity definition", e);
        }
    }

    @Override
    public Optional<ScopeQuantityDefinition> findDefinition(UUID tenantId, UUID definitionId) {
        String sql = """
                SELECT tenant_id, definition_id, scope_type, scope_id, quantity_code,
                       description, unit_code, planned_quantity, revision
                FROM planning_scope_quantity_definition
                WHERE tenant_id = ? AND definition_id = ?
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId);
            s.setObject(2, definitionId);
            try (ResultSet rs = s.executeQuery()) {
                return rs.next() ? Optional.of(mapDefinition(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw persistenceFailure("load scope quantity definition", e);
        }
    }

    @Override
    public List<ScopeQuantityDefinition> findDefinitionsForScope(UUID tenantId, String scopeType, String scopeId) {
        String sql = """
                SELECT tenant_id, definition_id, scope_type, scope_id, quantity_code,
                       description, unit_code, planned_quantity, revision
                FROM planning_scope_quantity_definition
                WHERE tenant_id = ? AND scope_type = ? AND scope_id = ?
                ORDER BY quantity_code, revision
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId);
            s.setString(2, scopeType);
            s.setString(3, scopeId);
            try (ResultSet rs = s.executeQuery()) {
                List<ScopeQuantityDefinition> result = new ArrayList<>();
                while (rs.next()) result.add(mapDefinition(rs));
                return List.copyOf(result);
            }
        } catch (SQLException e) {
            throw persistenceFailure("load quantities for scope", e);
        }
    }

    @Override
    public boolean appendMeasurement(QuantityMeasurement measurement) {
        String insert = """
                INSERT INTO planning_quantity_measurement
                    (tenant_id, measurement_id, definition_id, as_of_date, actual_quantity,
                     recorded_at, recorded_by, idempotency_key, source_reference)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                RETURNING measurement_id
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(insert)) {
            s.setObject(1, measurement.tenantId());
            s.setObject(2, measurement.id());
            s.setObject(3, measurement.definitionId());
            s.setObject(4, measurement.asOfDate());
            s.setBigDecimal(5, measurement.actualQuantity());
            s.setTimestamp(6, Timestamp.from(measurement.recordedAt()));
            s.setString(7, measurement.recordedBy());
            s.setString(8, measurement.idempotencyKey());
            s.setString(9, measurement.sourceReference());
            try (ResultSet rs = s.executeQuery()) {
                if (rs.next()) return true;
            }
            QuantityMeasurement existing = findByIdempotencyKey(c, measurement.tenantId(), measurement.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException("Measurement conflict but existing row was not found"));
            if (samePayload(existing, measurement)) return false;
            throw new IllegalArgumentException("Measurement idempotency key reused for different data");
        } catch (SQLException e) {
            throw persistenceFailure("append quantity measurement", e);
        }
    }

    @Override
    public List<QuantityMeasurement> findMeasurementHistory(UUID tenantId, UUID definitionId) {
        String sql = """
                SELECT tenant_id, measurement_id, definition_id, as_of_date, actual_quantity,
                       recorded_at, recorded_by, idempotency_key, source_reference
                FROM planning_quantity_measurement
                WHERE tenant_id = ? AND definition_id = ?
                ORDER BY as_of_date, recorded_at, measurement_id
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId);
            s.setObject(2, definitionId);
            try (ResultSet rs = s.executeQuery()) {
                List<QuantityMeasurement> result = new ArrayList<>();
                while (rs.next()) result.add(mapMeasurement(rs));
                return List.copyOf(result);
            }
        } catch (SQLException e) {
            throw persistenceFailure("load quantity measurement history", e);
        }
    }

    @Override
    public Optional<QuantityMeasurement> findLatestMeasurementAsOf(
            UUID tenantId, UUID definitionId, LocalDate asOfDate) {
        String sql = """
                SELECT tenant_id, measurement_id, definition_id, as_of_date, actual_quantity,
                       recorded_at, recorded_by, idempotency_key, source_reference
                FROM planning_quantity_measurement
                WHERE tenant_id = ? AND definition_id = ? AND as_of_date <= ?
                ORDER BY as_of_date DESC, recorded_at DESC, measurement_id DESC
                LIMIT 1
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId);
            s.setObject(2, definitionId);
            s.setObject(3, asOfDate);
            try (ResultSet rs = s.executeQuery()) {
                return rs.next() ? Optional.of(mapMeasurement(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw persistenceFailure("load latest quantity measurement", e);
        }
    }

    private Optional<QuantityMeasurement> findByIdempotencyKey(Connection c, UUID tenantId, String key)
            throws SQLException {
        String sql = """
                SELECT tenant_id, measurement_id, definition_id, as_of_date, actual_quantity,
                       recorded_at, recorded_by, idempotency_key, source_reference
                FROM planning_quantity_measurement WHERE tenant_id = ? AND idempotency_key = ?
                """;
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId);
            s.setString(2, key);
            try (ResultSet rs = s.executeQuery()) {
                return rs.next() ? Optional.of(mapMeasurement(rs)) : Optional.empty();
            }
        }
    }

    private ScopeQuantityDefinition mapDefinition(ResultSet rs) throws SQLException {
        return new ScopeQuantityDefinition(
                (UUID) rs.getObject("definition_id"), (UUID) rs.getObject("tenant_id"),
                rs.getString("scope_type"), rs.getString("scope_id"), rs.getString("quantity_code"),
                rs.getString("description"), rs.getString("unit_code"), rs.getBigDecimal("planned_quantity"),
                rs.getInt("revision"));
    }

    private QuantityMeasurement mapMeasurement(ResultSet rs) throws SQLException {
        return new QuantityMeasurement(
                (UUID) rs.getObject("measurement_id"), (UUID) rs.getObject("tenant_id"),
                (UUID) rs.getObject("definition_id"), rs.getObject("as_of_date", LocalDate.class),
                rs.getBigDecimal("actual_quantity"), rs.getTimestamp("recorded_at").toInstant(),
                rs.getString("recorded_by"), rs.getString("idempotency_key"), rs.getString("source_reference"));
    }

    private boolean samePayload(QuantityMeasurement a, QuantityMeasurement b) {
        return a.definitionId().equals(b.definitionId())
                && a.asOfDate().equals(b.asOfDate())
                && a.actualQuantity().compareTo(b.actualQuantity()) == 0
                && a.recordedBy().equals(b.recordedBy())
                && java.util.Objects.equals(a.sourceReference(), b.sourceReference());
    }

    private IllegalStateException persistenceFailure(String action, SQLException e) {
        return new IllegalStateException("Unable to " + action + " (SQLState=" + e.getSQLState() + ")", e);
    }
}
