package tech.kayys.numou.planning.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.planning.application.port.WorkBreakdownActivityLinkRepository;
import tech.kayys.numou.planning.domain.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class JdbcWorkBreakdownActivityLinkRepository implements WorkBreakdownActivityLinkRepository {
    private final DataSource dataSource;

    @Inject
    public JdbcWorkBreakdownActivityLinkRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void create(WorkBreakdownActivityLink link) {
        if (link == null) throw new IllegalArgumentException("Link cannot be null");
        String sql = "INSERT INTO work_breakdown_activity_link"
                + "(tenant_id, structure_id, node_id, plan_id, activity_id) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, link.tenantId());
            statement.setObject(2, link.structureId());
            statement.setObject(3, link.nodeId());
            statement.setObject(4, link.planId().value());
            statement.setObject(5, link.activityId().value());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to create WBS activity link", e);
        }
    }

    @Override
    public List<WorkBreakdownActivityLink> findByStructure(UUID tenantId, UUID structureId) {
        return find("tenant_id = ? AND structure_id = ?", tenantId, structureId);
    }

    @Override
    public List<WorkBreakdownActivityLink> findByPlan(UUID tenantId, UUID planId) {
        return find("tenant_id = ? AND plan_id = ?", tenantId, planId);
    }

    private List<WorkBreakdownActivityLink> find(String predicate, UUID tenantId, UUID id) {
        if (tenantId == null || id == null) throw new IllegalArgumentException("Tenant and lookup id are required");
        String sql = "SELECT tenant_id, structure_id, node_id, plan_id, activity_id "
                + "FROM work_breakdown_activity_link WHERE " + predicate
                + " ORDER BY structure_id, node_id, plan_id, activity_id";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, id);
            List<WorkBreakdownActivityLink> result = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new WorkBreakdownActivityLink(
                            rows.getObject("tenant_id", UUID.class),
                            rows.getObject("structure_id", UUID.class),
                            rows.getObject("node_id", UUID.class),
                            new PlanId(rows.getObject("plan_id", UUID.class)),
                            new ActivityId(rows.getObject("activity_id", UUID.class))));
                }
            }
            return List.copyOf(result);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to query WBS activity links", e);
        }
    }
}
