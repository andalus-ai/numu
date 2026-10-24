package tech.kayys.numou.planning.application.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tech.kayys.numou.planning.application.port.out.CostEvidenceReader;

class CostEvidenceSourceAdapterTest {
    @Test
    void forwardsTenantAndScopeToReaderAndReturnsImmutableResults() {
        UUID tenant = UUID.randomUUID();
        UUID structure = UUID.randomUUID();
        UUID baseline = UUID.randomUUID();
        UUID node = UUID.randomUUID();
        var adapter = new CostEvidenceSourceAdapter(new CostEvidenceReader() {
            @Override
            public List<tech.kayys.numou.planning.domain.BudgetedCostEvidence> findApprovedBudgets(
                    UUID tenantId, UUID structureId, UUID baselineId, List<UUID> nodeIds) {
                assertEquals(tenant, tenantId);
                assertEquals(structure, structureId);
                assertEquals(baseline, baselineId);
                assertEquals(List.of(node), nodeIds);
                return List.of();
            }

            @Override
            public List<tech.kayys.numou.planning.domain.CumulativeActualCostEvidence> findApprovedActualCosts(
                    UUID tenantId, UUID structureId, LocalDate asOfDate, List<UUID> nodeIds) {
                assertEquals(tenant, tenantId);
                assertEquals(structure, structureId);
                assertEquals(LocalDate.of(2026, 10, 24), asOfDate);
                assertEquals(List.of(node), nodeIds);
                return List.of();
            }
        });

        assertEquals(List.of(), adapter.findApproved(tenant, structure, baseline, List.of(node)));
        assertEquals(List.of(), adapter.findApproved(
                tenant, structure, LocalDate.of(2026, 10, 24), List.of(node)));
    }

    @Test
    void rejectsDuplicateNodesBeforeCallingReader() {
        var adapter = new CostEvidenceSourceAdapter(new CostEvidenceReader() {
            @Override
            public List<tech.kayys.numou.planning.domain.BudgetedCostEvidence> findApprovedBudgets(
                    UUID tenantId, UUID structureId, UUID baselineId, List<UUID> nodeIds) {
                throw new AssertionError("Reader must not be called for invalid scope");
            }

            @Override
            public List<tech.kayys.numou.planning.domain.CumulativeActualCostEvidence> findApprovedActualCosts(
                    UUID tenantId, UUID structureId, LocalDate asOfDate, List<UUID> nodeIds) {
                throw new AssertionError("Reader must not be called for invalid scope");
            }
        });
        UUID node = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> adapter.findApproved(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        List.of(node, node)));
    }

    @Test
    void rejectsNullReaderResultsRatherThanSilentlyTreatingThemAsEmpty() {
        var adapter = new CostEvidenceSourceAdapter(new CostEvidenceReader() {
            @Override
            public List<tech.kayys.numou.planning.domain.BudgetedCostEvidence> findApprovedBudgets(
                    UUID tenantId, UUID structureId, UUID baselineId, List<UUID> nodeIds) {
                return null;
            }

            @Override
            public List<tech.kayys.numou.planning.domain.CumulativeActualCostEvidence> findApprovedActualCosts(
                    UUID tenantId, UUID structureId, LocalDate asOfDate, List<UUID> nodeIds) {
                return List.of();
            }
        });

        assertThrows(NullPointerException.class,
                () -> adapter.findApproved(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        List.of(UUID.randomUUID())));
    }
}
