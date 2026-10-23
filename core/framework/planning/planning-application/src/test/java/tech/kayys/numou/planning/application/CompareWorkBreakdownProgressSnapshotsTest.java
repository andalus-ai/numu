package tech.kayys.numou.planning.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.planning.application.port.WorkBreakdownProgressSnapshotRepository;
import tech.kayys.numou.planning.domain.ProgressVariance;
import tech.kayys.numou.planning.domain.WorkBreakdownProgressSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CompareWorkBreakdownProgressSnapshotsTest {
    @Test
    void reportsVarianceInPercentagePointsForSameWbsNode() {
        UUID tenant = UUID.randomUUID(), structure = UUID.randomUUID(), node = UUID.randomUUID();
        WorkBreakdownProgressSnapshot baseline = snapshot(tenant, structure, node, "2026-10-01", "40");
        WorkBreakdownProgressSnapshot current = snapshot(tenant, structure, node, "2026-10-23", "62.5");
        WorkBreakdownProgressSnapshotRepository repository = repository(Map.of(
                baseline.snapshotId(), baseline, current.snapshotId(), current));
        ProgressVariance variance = new CompareWorkBreakdownProgressSnapshots(repository)
                .execute(tenant, baseline.snapshotId(), current.snapshotId());
        assertEquals(0, new BigDecimal("22.50").compareTo(variance.variancePercentagePoints()));
    }

    @Test
    void rejectsSnapshotsFromDifferentWbsNodes() {
        UUID tenant = UUID.randomUUID(), structure = UUID.randomUUID();
        WorkBreakdownProgressSnapshot baseline = snapshot(tenant, structure, UUID.randomUUID(), "2026-10-01", "40");
        WorkBreakdownProgressSnapshot current = snapshot(tenant, structure, UUID.randomUUID(), "2026-10-23", "62.5");
        var useCase = new CompareWorkBreakdownProgressSnapshots(repository(Map.of(
                baseline.snapshotId(), baseline, current.snapshotId(), current)));
        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(tenant, baseline.snapshotId(), current.snapshotId()));
    }

    private static WorkBreakdownProgressSnapshotRepository repository(Map<UUID, WorkBreakdownProgressSnapshot> snapshots) {
        return new WorkBreakdownProgressSnapshotRepository() {
            public void create(WorkBreakdownProgressSnapshot snapshot) { throw new UnsupportedOperationException(); }
            public Optional<WorkBreakdownProgressSnapshot> findById(UUID tenantId, UUID snapshotId) {
                WorkBreakdownProgressSnapshot snapshot = snapshots.get(snapshotId);
                return snapshot != null && snapshot.tenantId().equals(tenantId) ? Optional.of(snapshot) : Optional.empty();
            }
            public List<WorkBreakdownProgressSnapshot> findHistory(UUID tenantId, UUID structureId, UUID nodeId,
                    LocalDate from, LocalDate through) { throw new UnsupportedOperationException(); }
        };
    }

    private static WorkBreakdownProgressSnapshot snapshot(UUID tenant, UUID structure, UUID node, String date, String percent) {
        BigDecimal completion = new BigDecimal(percent);
        return new WorkBreakdownProgressSnapshot(UUID.randomUUID(), tenant, structure, node, UUID.randomUUID(),
                1, LocalDate.parse(date), "leaf-weight-v1", completion, BigDecimal.ONE, 1,
                Instant.parse(date + "T10:00:00Z"),
                List.of(new WorkBreakdownProgressSnapshot.ActivityContribution(UUID.randomUUID(), completion, BigDecimal.ONE)));
    }
}
