package tech.kayys.numou.planning.adapter.postgres;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.planning.domain.WorkBreakdownNode;
import tech.kayys.numou.planning.domain.WorkBreakdownStructure;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JdbcWorkBreakdownRepositoryIntegrationTest {
    @Inject DataSource dataSource;
    @Inject JdbcWorkBreakdownRepository repository;

    @Test
    void persistsSnapshotAndScopesLookupByTenant() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        UUID root = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        WorkBreakdownStructure structure = WorkBreakdownStructure.create(UUID.randomUUID(),
                "WBS-" + UUID.randomUUID(), List.of(
                        new WorkBreakdownNode(root, null, "ROOT", "Project", 0),
                        new WorkBreakdownNode(child, root, "PHASE", "Phase", 0)));

        repository.create(tenant, structure);

        WorkBreakdownStructure loaded = repository.findById(tenant, structure.id()).orElseThrow();
        assertEquals(structure.id(), loaded.id());
        assertEquals(structure.code(), loaded.code());
        assertEquals(java.util.Set.of(root, child), loaded.nodes().stream()
                .map(WorkBreakdownNode::id).collect(java.util.stream.Collectors.toSet()));
        assertTrue(repository.findById(otherTenant, structure.id()).isEmpty());

        assertThrows(Exception.class, () -> mutateNode(tenant, structure.id(), child));
    }

    @Test
    void rollsBackHeaderWhenCreateFailsOnDuplicateTenantScopedCode() {
        UUID tenant = UUID.randomUUID();
        WorkBreakdownStructure first = singleNode("WBS-" + UUID.randomUUID());
        repository.create(tenant, first);

        WorkBreakdownStructure duplicateCode = singleNode(first.code().toLowerCase(java.util.Locale.ROOT));
        assertThrows(RuntimeException.class, () -> repository.create(tenant, duplicateCode));
        assertTrue(repository.findById(tenant, duplicateCode.id()).isEmpty());
    }

    private WorkBreakdownStructure singleNode(String code) {
        return WorkBreakdownStructure.create(UUID.randomUUID(), code,
                List.of(new WorkBreakdownNode(UUID.randomUUID(), null, "ROOT", "Project", 0)));
    }

    private void mutateNode(UUID tenantId, UUID structureId, UUID nodeId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE work_breakdown_node SET name = 'mutated' WHERE tenant_id = ? AND structure_id = ? AND node_id = ?")) {
            statement.setObject(1, tenantId);
            statement.setObject(2, structureId);
            statement.setObject(3, nodeId);
            statement.executeUpdate();
        }
    }
}
