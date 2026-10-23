package tech.kayys.numou.planning.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.planning.application.port.QuantityCorrectionRepository;
import tech.kayys.numou.planning.domain.QuantityMeasurementCorrection;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@ApplicationScoped
public class JdbcQuantityCorrectionRepository implements QuantityCorrectionRepository {
    private final DataSource dataSource;
    @Inject public JdbcQuantityCorrectionRepository(DataSource dataSource) { this.dataSource = dataSource; }

    @Override public boolean appendCorrection(QuantityMeasurementCorrection correction) {
        String sql = """
                INSERT INTO planning_quantity_measurement_correction
                    (tenant_id, correction_id, definition_id, original_measurement_id,
                     replacement_measurement_id, recorded_at, recorded_by, reason, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, idempotency_key) DO NOTHING RETURNING correction_id
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, correction.tenantId()); s.setObject(2, correction.id());
            s.setObject(3, correction.definitionId()); s.setObject(4, correction.originalMeasurementId());
            s.setObject(5, correction.replacementMeasurementId()); s.setTimestamp(6, Timestamp.from(correction.recordedAt()));
            s.setString(7, correction.recordedBy()); s.setString(8, correction.reason()); s.setString(9, correction.idempotencyKey());
            try (ResultSet rs = s.executeQuery()) { if (rs.next()) return true; }
            var existing = findByIdempotencyKey(c, correction.tenantId(), correction.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException("Correction conflict but existing row was not found"));
            if (samePayload(existing, correction)) return false;
            throw new IllegalArgumentException("Correction idempotency key reused for different data");
        } catch (SQLException e) { throw persistenceFailure("append quantity measurement correction", e); }
    }

    @Override public List<QuantityMeasurementCorrection> findCorrections(UUID tenant, UUID definition, UUID original) {
        return query("""
                SELECT tenant_id, correction_id, definition_id, original_measurement_id,
                       replacement_measurement_id, recorded_at, recorded_by, reason, idempotency_key
                FROM planning_quantity_measurement_correction
                WHERE tenant_id = ? AND definition_id = ? AND original_measurement_id = ?
                ORDER BY recorded_at, correction_id
                """, tenant, definition, original);
    }

    @Override public List<QuantityMeasurementCorrection> findCorrectionsForDefinition(UUID tenant, UUID definition) {
        return query("""
                SELECT tenant_id, correction_id, definition_id, original_measurement_id,
                       replacement_measurement_id, recorded_at, recorded_by, reason, idempotency_key
                FROM planning_quantity_measurement_correction
                WHERE tenant_id = ? AND definition_id = ?
                ORDER BY recorded_at, correction_id
                """, tenant, definition);
    }

    private List<QuantityMeasurementCorrection> query(String sql, UUID tenant, UUID definition, UUID... original) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenant); s.setObject(2, definition);
            if (original.length > 0) s.setObject(3, original[0]);
            try (ResultSet rs = s.executeQuery()) {
                List<QuantityMeasurementCorrection> result = new ArrayList<>();
                while (rs.next()) result.add(map(rs));
                return List.copyOf(result);
            }
        } catch (SQLException e) { throw persistenceFailure("load quantity measurement corrections", e); }
    }

    private Optional<QuantityMeasurementCorrection> findByIdempotencyKey(Connection c, UUID tenant, String key) throws SQLException {
        String sql = """
                SELECT tenant_id, correction_id, definition_id, original_measurement_id,
                       replacement_measurement_id, recorded_at, recorded_by, reason, idempotency_key
                FROM planning_quantity_measurement_correction WHERE tenant_id = ? AND idempotency_key = ?
                """;
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenant); s.setString(2, key);
            try (ResultSet rs = s.executeQuery()) { return rs.next() ? Optional.of(map(rs)) : Optional.empty(); }
        }
    }

    private QuantityMeasurementCorrection map(ResultSet rs) throws SQLException {
        return new QuantityMeasurementCorrection((UUID) rs.getObject("correction_id"), (UUID) rs.getObject("tenant_id"),
                (UUID) rs.getObject("definition_id"), (UUID) rs.getObject("original_measurement_id"),
                (UUID) rs.getObject("replacement_measurement_id"), rs.getTimestamp("recorded_at").toInstant(),
                rs.getString("recorded_by"), rs.getString("reason"), rs.getString("idempotency_key"));
    }
    private boolean samePayload(QuantityMeasurementCorrection a, QuantityMeasurementCorrection b) {
        return a.tenantId().equals(b.tenantId()) && a.definitionId().equals(b.definitionId())
                && a.originalMeasurementId().equals(b.originalMeasurementId())
                && a.replacementMeasurementId().equals(b.replacementMeasurementId())
                && a.recordedBy().equals(b.recordedBy()) && a.reason().equals(b.reason())
                && a.recordedAt().equals(b.recordedAt());
    }
    private IllegalStateException persistenceFailure(String action, SQLException e) {
        return new IllegalStateException("Unable to " + action + " (SQLState=" + e.getSQLState() + ")", e);
    }
}
