package tech.kayys.numou.planning.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.planning.application.port.ProgressSnapshotDecisionRepository;
import tech.kayys.numou.planning.application.port.WorkBreakdownProgressBaselineRepository;
import tech.kayys.numou.planning.application.port.WorkBreakdownProgressSnapshotRepository;
import tech.kayys.numou.planning.domain.ProgressSnapshotDecision;
import tech.kayys.numou.planning.domain.ProgressVariance;
import tech.kayys.numou.planning.domain.WorkBreakdownProgressBaseline;
import tech.kayys.numou.planning.domain.WorkBreakdownProgressSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProgressBaselineUseCasesTest {
    @Test
    void baselineRequiresLatestDecisionToBeApproved() {
        UUID tenant = UUID.randomUUID(), structure = UUID.randomUUID(), node = UUID.randomUUID();
        WorkBreakdownProgressSnapshot snapshot = snapshot(tenant, structure, node, "2026-10-01", "40");
        InMemorySnapshots snapshots = new InMemorySnapshots(Map.of(snapshot.snapshotId(), snapshot));
        InMemoryDecisions decisions = new InMemoryDecisions();
        InMemoryBaselines baselines = new InMemoryBaselines();
        CreateWorkBreakdownProgressBaseline useCase =
                new CreateWorkBreakdownProgressBaseline(snapshots, decisions, baselines);
        WorkBreakdownProgressBaseline baseline = baseline(tenant, structure, node, snapshot, "2026-10-01");

        assertThrows(IllegalArgumentException.class, () -> useCase.execute(baseline));
        decisions.latest = Optional.of(decision(tenant, snapshot, ProgressSnapshotDecision.Outcome.REJECTED));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(baseline));
        decisions.latest = Optional.of(decision(tenant, snapshot, ProgressSnapshotDecision.Outcome.APPROVED));
        useCase.execute(baseline);
        assertEquals(baseline, baselines.created);
    }

    @Test
    void comparesCurrentSnapshotToBaselineAndReturnsPercentagePointVariance() {
        UUID tenant = UUID.randomUUID(), structure = UUID.randomUUID(), node = UUID.randomUUID();
        WorkBreakdownProgressSnapshot source = snapshot(tenant, structure, node, "2026-10-01", "40");
        WorkBreakdownProgressSnapshot current = snapshot(tenant, structure, node, "2026-10-23", "62.5");
        WorkBreakdownProgressBaseline baseline = baseline(tenant, structure, node, source, "2026-10-01");
        InMemoryBaselines baselines = new InMemoryBaselines();
        baselines.created = baseline;
        InMemorySnapshots snapshots = new InMemorySnapshots(Map.of(
                source.snapshotId(), source, current.snapshotId(), current));

        ProgressVariance variance = new CompareProgressSnapshotToBaseline(baselines, snapshots)
                .execute(tenant, baseline.baselineId(), current.snapshotId());

        assertEquals(0, new BigDecimal("22.50").compareTo(variance.variancePercentagePoints()));
        assertEquals(source.snapshotId(), variance.baselineSnapshotId());
        assertEquals(current.snapshotId(), variance.currentSnapshotId());
    }

    private static WorkBreakdownProgressBaseline baseline(UUID tenant, UUID structure, UUID node,
            WorkBreakdownProgressSnapshot source, String effectiveDate) {
        return new WorkBreakdownProgressBaseline(UUID.randomUUID(), tenant, structure, node, source.snapshotId(),
                "BL-" + UUID.randomUUID(), "Approved baseline", LocalDate.parse(effectiveDate),
                UUID.randomUUID(), Instant.parse("2026-10-23T10:00:00Z"));
    }

    private static ProgressSnapshotDecision decision(UUID tenant, WorkBreakdownProgressSnapshot snapshot,
            ProgressSnapshotDecision.Outcome outcome) {
        return new ProgressSnapshotDecision(UUID.randomUUID(), tenant, snapshot.snapshotId(), UUID.randomUUID(),
                outcome, "Reviewed for baseline", Instant.parse("2026-10-02T10:00:00Z"));
    }

    private static WorkBreakdownProgressSnapshot snapshot(UUID tenant, UUID structure, UUID node, String date, String percent) {
        BigDecimal completion = new BigDecimal(percent);
        return new WorkBreakdownProgressSnapshot(UUID.randomUUID(), tenant, structure, node, UUID.randomUUID(),
                1, LocalDate.parse(date), "leaf-weight-v1", completion, BigDecimal.ONE, 1,
                Instant.parse(date + "T10:00:00Z"),
                List.of(new WorkBreakdownProgressSnapshot.ActivityContribution(UUID.randomUUID(), completion, BigDecimal.ONE)));
    }

    private static final class InMemorySnapshots implements WorkBreakdownProgressSnapshotRepository {
        private final Map<UUID, WorkBreakdownProgressSnapshot> values;
        private InMemorySnapshots(Map<UUID, WorkBreakdownProgressSnapshot> values) { this.values = values; }
        public void create(WorkBreakdownProgressSnapshot snapshot) { throw new UnsupportedOperationException(); }
        public Optional<WorkBreakdownProgressSnapshot> findById(UUID tenantId, UUID id) {
            WorkBreakdownProgressSnapshot value = values.get(id);
            return value != null && value.tenantId().equals(tenantId) ? Optional.of(value) : Optional.empty();
        }
        public List<WorkBreakdownProgressSnapshot> findHistory(UUID tenantId, UUID structureId, UUID nodeId,
                LocalDate from, LocalDate through) { throw new UnsupportedOperationException(); }
    }

    private static final class InMemoryDecisions implements ProgressSnapshotDecisionRepository {
        private Optional<ProgressSnapshotDecision> latest = Optional.empty();
        public void append(ProgressSnapshotDecision decision) { latest = Optional.of(decision); }
        public List<ProgressSnapshotDecision> findHistory(UUID tenantId, UUID snapshotId) {
            return latest.filter(d -> d.tenantId().equals(tenantId) && d.snapshotId().equals(snapshotId)).map(List::of).orElseGet(List::of);
        }
        public Optional<ProgressSnapshotDecision> findLatest(UUID tenantId, UUID snapshotId) {
            return latest.filter(d -> d.tenantId().equals(tenantId) && d.snapshotId().equals(snapshotId));
        }
    }

    private static final class InMemoryBaselines implements WorkBreakdownProgressBaselineRepository {
        private WorkBreakdownProgressBaseline created;
        public void create(WorkBreakdownProgressBaseline baseline) { created = baseline; }
        public Optional<WorkBreakdownProgressBaseline> findById(UUID tenantId, UUID baselineId) {
            return Optional.ofNullable(created).filter(b -> b.tenantId().equals(tenantId) && b.baselineId().equals(baselineId));
        }
        public List<WorkBreakdownProgressBaseline> findByScope(UUID tenantId, UUID structureId, UUID nodeId) {
            if (created == null || !created.tenantId().equals(tenantId)
                    || !created.structureId().equals(structureId) || !created.nodeId().equals(nodeId)) return List.of();
            return new ArrayList<>(List.of(created));
        }
    }
}
