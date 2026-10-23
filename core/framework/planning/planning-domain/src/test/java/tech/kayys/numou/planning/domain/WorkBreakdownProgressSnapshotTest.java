package tech.kayys.numou.planning.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkBreakdownProgressSnapshotTest {
    @Test
    void capturesImmutableInputsThatReproduceWeightedResult() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        WorkBreakdownProgressSnapshot snapshot = new WorkBreakdownProgressSnapshot(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 7, LocalDate.parse("2026-10-23"), "leaf-weight-v1",
                new BigDecimal("87.50"), new BigDecimal("4"), 2, Instant.parse("2026-10-23T03:00:00Z"),
                List.of(
                        new WorkBreakdownProgressSnapshot.ActivityContribution(a, new BigDecimal("50"), new BigDecimal("1")),
                        new WorkBreakdownProgressSnapshot.ActivityContribution(b, new BigDecimal("100"), new BigDecimal("3"))));

        assertEquals(2, snapshot.contributions().size());
        assertEquals(7, snapshot.planRevision());
        assertEquals(new BigDecimal("87.50"), snapshot.completionPercent());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.contributions().clear());
    }

    @Test
    void rejectsResultThatDoesNotMatchFrozenInputs() {
        assertThrows(IllegalArgumentException.class, () -> new WorkBreakdownProgressSnapshot(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 0, LocalDate.parse("2026-10-23"), "leaf-weight-v1",
                new BigDecimal("90"), new BigDecimal("4"), 2, Instant.parse("2026-10-23T03:00:00Z"),
                List.of(
                        new WorkBreakdownProgressSnapshot.ActivityContribution(UUID.randomUUID(), new BigDecimal("50"), new BigDecimal("1")),
                        new WorkBreakdownProgressSnapshot.ActivityContribution(UUID.randomUUID(), new BigDecimal("100"), new BigDecimal("3")))));
    }

    @Test
    void rejectsDuplicateActivitiesAndNonPositiveWeights() {
        UUID activity = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new WorkBreakdownProgressSnapshot(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 0, LocalDate.parse("2026-10-23"), "leaf-weight-v1",
                new BigDecimal("50"), new BigDecimal("2"), 2, Instant.parse("2026-10-23T03:00:00Z"),
                List.of(
                        new WorkBreakdownProgressSnapshot.ActivityContribution(activity, new BigDecimal("50"), BigDecimal.ONE),
                        new WorkBreakdownProgressSnapshot.ActivityContribution(activity, new BigDecimal("50"), BigDecimal.ONE))));
        assertThrows(IllegalArgumentException.class, () -> new WorkBreakdownProgressSnapshot.ActivityContribution(
                UUID.randomUUID(), new BigDecimal("50"), BigDecimal.ZERO));
    }
}
