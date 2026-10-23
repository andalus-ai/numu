package tech.kayys.numou.planning.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProgressRollupTest {
    private static final LocalDate AS_OF = LocalDate.parse("2026-10-20");
    private static final UUID TENANT = UUID.randomUUID();

    @Test
    void calculatesWeightedCompletionAcrossDifferentWorkTypes() {
        var first = contribution("feature-a", "100", "1");
        var second = contribution("package-b", "50", "3");

        var rollup = ProgressRollup.aggregate(List.of(first, second));

        assertEquals(new BigDecimal("62.50"), rollup.completionPercent());
        assertEquals(new BigDecimal("4"), rollup.totalWeight());
        assertEquals(2, rollup.includedActivityCount());
        assertEquals(TENANT, rollup.tenantId());
        assertEquals(AS_OF, rollup.asOfDate());
    }

    @Test
    void refusesDuplicateActivitySoParentAndChildCannotAccidentallyBeCountedTwice() {
        var item = contribution("same", "40", "1");
        var duplicateActivity = new ProgressContribution(UUID.randomUUID(), TENANT, item.activityId(),
                AS_OF, new BigDecimal("80"), BigDecimal.ONE);

        assertThrows(IllegalArgumentException.class,
                () -> ProgressRollup.aggregate(List.of(item, duplicateActivity)));
    }

    @Test
    void refusesMixedTenantsAndSnapshotDates() {
        var item = contribution("one", "40", "1");
        var otherTenant = new ProgressContribution(UUID.randomUUID(), UUID.randomUUID(),
                ActivityId.generate(), AS_OF, new BigDecimal("50"), BigDecimal.ONE);
        var otherDate = new ProgressContribution(UUID.randomUUID(), TENANT,
                ActivityId.generate(), AS_OF.plusDays(1), new BigDecimal("50"), BigDecimal.ONE);

        assertThrows(IllegalArgumentException.class,
                () -> ProgressRollup.aggregate(List.of(item, otherTenant)));
        assertThrows(IllegalArgumentException.class,
                () -> ProgressRollup.aggregate(List.of(item, otherDate)));
    }

    @Test
    void requiresPositiveWeightAndNonEmptyInputs() {
        assertThrows(IllegalArgumentException.class, () -> contribution("zero", "20", "0"));
        assertThrows(IllegalArgumentException.class, () -> ProgressRollup.aggregate(List.of()));
    }

    private ProgressContribution contribution(String code, String percent, String weight) {
        var assessment = new ProgressAssessment(new ActivityId(UUID.nameUUIDFromBytes(code.getBytes())),
                AS_OF, BigDecimal.ZERO, new BigDecimal(percent), new BigDecimal(percent), ProgressAssessment.Status.ON_TRACK);
        return ProgressContribution.from(UUID.randomUUID(), TENANT, assessment, new BigDecimal(weight));
    }
}
