package tech.kayys.numou.planning.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.planning.application.port.WorkBreakdownProgressBaselineRepository;
import tech.kayys.numou.planning.domain.WorkBreakdownProgressBaseline;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL adapter for immutable, tenant-scoped progress baselines. */
@ApplicationScoped
public class JdbcWorkBreakdownProgressBaselineRepository implements WorkBreakdownProgressBaselineRepository {
    private final DataSource dataSource;

    @Inject
    public JdbcWorkBreakdownProgressBaselineRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void create(WorkBreakdownProgressBaseline baseline) {
        if (baseline == null) throw new IllegalArgumentException("Baseline cannot be null");
        String sql = "INSERT INTO work_breakdown_progress_baseline "
                + "(tenant_id, baseline_id, structure_id, node_id, source_snapshot_id, baseline_code, name, effective_date, created_by, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, baseline.tenantId());
            s.setObject(2, baseline.baselineId());
            s.setObject(3, baseline.structureId());
            s.setObject(4, baseline.nodeId());
            s.setObject(5, baseline.sourceSnapshotId());
            s.setString(6, baseline.baselineCode());
            s.setString(7, baseline.name());
            s.setObject(8, baseline.effectiveDate());
            s.setObject(9, baseline.createdBy());
            s.setObject(10, OffsetDateTime.ofInstant(baseline.createdAt(), ZoneOffset.UTC));
            s.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to create progress baseline", e);
        }
    }

    @Override
    public Optional<WorkBreakdownProgressBaseline> findById(UUID tenantId, UUID baselineId) {
        requireIds(tenantId, baselineId);
        String sql = "SELECT * FROM work_breakdown_progress_baseline WHERE tenant_id = ? AND baseline_id = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId);
            s.setObject(2, baselineId);
            try (ResultSet rs = s.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to find progress baseline", e);
        }
    }

    @Override
    public List<WorkBreakdownProgressBaseline> findByScope(UUID tenantId, UUID structureId, UUID nodeId) {
        if (tenantId == null || structureId == null || nodeId == null)
            throw new IllegalArgumentException("Tenant, structure and node identifiers are required");
        String sql = "SELECT * FROM work_breakdown_progress_baseline WHERE tenant_id = ? AND structure_id = ? AND node_id = ? "
                + "ORDER BY effective_date DESC, created_at DESC, baseline_id DESC";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, tenantId);
            s.setObject(2, structureId);
            s.setObject(3, nodeId);
            List<WorkBreakdownProgressBaseline> result = new ArrayList<>();
            try (ResultSet rs = s.executeQuery()) {
                while (rs.next()) result.add(map(rs));
            }
            return List.copyOf(result);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to list progress baselines", e);
        }
    }

    private static WorkBreakdownProgressBaseline map(ResultSet rs) throws SQLException {
        OffsetDateTime created = rs.getObject("created_at", OffsetDateTime.class);
        return new WorkBreakdownProgressBaseline(
                rs.getObject("baseline_id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("structure_id", UUID.class), rs.getObject("node_id", UUID.class),
                rs.getObject("source_snapshot_id", UUID.class), rs.getString("baseline_code"),
                rs.getString("name"), rs.getObject("effective_date", LocalDate.class),
                rs.getObject("created_by", UUID.class), created.toInstant());
    }

    private static void requireIds(UUID tenantId, UUID baselineId) {
        if (tenantId == null || baselineId == null)
            throw new IllegalArgumentException("Tenant and baseline identifiers are required");
    }
}
