package tech.kayys.numou.planning.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.planning.application.port.WorkBreakdownRepository;
import tech.kayys.numou.planning.domain.WorkBreakdownNode;
import tech.kayys.numou.planning.domain.WorkBreakdownStructure;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL adapter storing immutable WBS snapshots with tenant-scoped keys. */
@ApplicationScoped
public class JdbcWorkBreakdownRepository implements WorkBreakdownRepository {
    private final DataSource dataSource;

    @Inject
    public JdbcWorkBreakdownRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void create(UUID tenantId, WorkBreakdownStructure structure) {
        if (tenantId == null) throw new IllegalArgumentException("Tenant id cannot be null");
        if (structure == null) throw new IllegalArgumentException("Structure cannot be null");

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement header = connection.prepareStatement(
                        "INSERT INTO work_breakdown_structure(tenant_id, structure_id, code) VALUES (?, ?, ?)")) {
                    header.setObject(1, tenantId);
                    header.setObject(2, structure.id());
                    header.setString(3, structure.code());
                    header.executeUpdate();
                }

                String sql = "INSERT INTO work_breakdown_node(tenant_id, structure_id, node_id, parent_id, code, name, position) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement nodeInsert = connection.prepareStatement(sql)) {
                    for (WorkBreakdownNode node : structure.nodes()) {
                        nodeInsert.setObject(1, tenantId);
                        nodeInsert.setObject(2, structure.id());
                        nodeInsert.setObject(3, node.id());
                        nodeInsert.setObject(4, node.parentId());
                        nodeInsert.setString(5, node.code());
                        nodeInsert.setString(6, node.name());
                        nodeInsert.setInt(7, node.position());
                        nodeInsert.addBatch();
                    }
                    nodeInsert.executeBatch();
                }
                connection.commit();
            } catch (Exception failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("Unable to persist work breakdown structure", failure);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to create work breakdown structure", e);
        }
    }

    @Override
    public Optional<WorkBreakdownStructure> findById(UUID tenantId, UUID structureId) {
        if (tenantId == null) throw new IllegalArgumentException("Tenant id cannot be null");
        if (structureId == null) throw new IllegalArgumentException("Structure id cannot be null");

        String headerSql = "SELECT code FROM work_breakdown_structure WHERE tenant_id = ? AND structure_id = ?";
        String nodeSql = "SELECT node_id, parent_id, code, name, position FROM work_breakdown_node "
                + "WHERE tenant_id = ? AND structure_id = ? ORDER BY position, code";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement header = connection.prepareStatement(headerSql)) {
            header.setObject(1, tenantId);
            header.setObject(2, structureId);
            String code;
            try (ResultSet row = header.executeQuery()) {
                if (!row.next()) return Optional.empty();
                code = row.getString("code");
            }

            List<WorkBreakdownNode> nodes = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(nodeSql)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, structureId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        nodes.add(new WorkBreakdownNode(
                                rows.getObject("node_id", UUID.class),
                                rows.getObject("parent_id", UUID.class),
                                rows.getString("code"),
                                rows.getString("name"),
                                rows.getInt("position")));
                    }
                }
            }
            return Optional.of(WorkBreakdownStructure.create(structureId, code, nodes));
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load work breakdown structure", e);
        }
    }
}
