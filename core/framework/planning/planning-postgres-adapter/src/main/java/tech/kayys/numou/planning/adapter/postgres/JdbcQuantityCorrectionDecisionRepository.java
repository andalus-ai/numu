package tech.kayys.numou.planning.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.planning.application.port.QuantityCorrectionDecisionRepository;
import tech.kayys.numou.planning.domain.QuantityCorrectionDecision;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@ApplicationScoped
public class JdbcQuantityCorrectionDecisionRepository implements QuantityCorrectionDecisionRepository {
    private final DataSource dataSource;
    @Inject public JdbcQuantityCorrectionDecisionRepository(DataSource dataSource) { this.dataSource = dataSource; }

    @Override public boolean appendDecision(QuantityCorrectionDecision decision) {
        String sql = """
                INSERT INTO planning_quantity_correction_decision
                    (tenant_id, decision_id, definition_id, correction_id, decision, decided_at,
                     decided_by, reason, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, idempotency_key) DO NOTHING RETURNING decision_id
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, decision.tenantId()); s.setObject(2, decision.id());
            s.setObject(3, decision.definitionId()); s.setObject(4, decision.correctionId());
            s.setString(5, decision.decision().name()); s.setTimestamp(6, Timestamp.from(decision.decidedAt()));
            s.setString(7, decision.decidedBy()); s.setString(8, decision.reason()); s.setString(9, decision.idempotencyKey());
            try (ResultSet rs = s.executeQuery()) { if (rs.next()) return true; }
            var existing = findByIdempotencyKey(c, decision.tenantId(), decision.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException("Decision conflict but existing row was not found"));
            if (samePayload(existing, decision)) return false;
            throw new IllegalArgumentException("Decision idempotency key reused for different data");
        } catch (SQLException e) { throw persistenceFailure("append quantity correction decision", e); }
    }

    @Override public List<QuantityCorrectionDecision> findDecisions(UUID tenant, UUID definition) {
        String sql = """
                SELECT tenant_id, decision_id, definition_id, correction_id, decision, decided_at,
                       decided_by, reason, idempotency_key
                FROM planning_quantity_correction_decision
                WHERE tenant_id = ? AND definition_id = ?
                ORDER BY decided_at, decision_id
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenant); s.setObject(2, definition);
            try (ResultSet rs = s.executeQuery()) {
                List<QuantityCorrectionDecision> result = new ArrayList<>();
                while (rs.next()) result.add(map(rs));
                return List.copyOf(result);
            }
        } catch (SQLException e) { throw persistenceFailure("load quantity correction decisions", e); }
    }

    private Optional<QuantityCorrectionDecision> findByIdempotencyKey(Connection c, UUID tenant, String key) throws SQLException {
        String sql = """
                SELECT tenant_id, decision_id, definition_id, correction_id, decision, decided_at,
                       decided_by, reason, idempotency_key
                FROM planning_quantity_correction_decision WHERE tenant_id = ? AND idempotency_key = ?
                """;
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenant); s.setString(2, key);
            try (ResultSet rs = s.executeQuery()) { return rs.next() ? Optional.of(map(rs)) : Optional.empty(); }
        }
    }
    private QuantityCorrectionDecision map(ResultSet rs) throws SQLException {
        return new QuantityCorrectionDecision((UUID) rs.getObject("decision_id"), (UUID) rs.getObject("tenant_id"),
                (UUID) rs.getObject("definition_id"), (UUID) rs.getObject("correction_id"),
                QuantityCorrectionDecision.Decision.valueOf(rs.getString("decision")), rs.getTimestamp("decided_at").toInstant(),
                rs.getString("decided_by"), rs.getString("reason"), rs.getString("idempotency_key"));
    }
    private boolean samePayload(QuantityCorrectionDecision a, QuantityCorrectionDecision b) {
        return a.tenantId().equals(b.tenantId()) && a.definitionId().equals(b.definitionId())
                && a.correctionId().equals(b.correctionId()) && a.decision() == b.decision()
                && a.decidedAt().equals(b.decidedAt()) && a.decidedBy().equals(b.decidedBy()) && a.reason().equals(b.reason());
    }
    private IllegalStateException persistenceFailure(String action, SQLException e) {
        return new IllegalStateException("Unable to " + action + " (SQLState=" + e.getSQLState() + ")", e);
    }
}
