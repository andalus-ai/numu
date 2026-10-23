package tech.kayys.numou.planning.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class QuantityBasedProgressAssessmentTest {
    @Test void convertsAcceptedQuantityToScheduleProgressAndTracksOverrunSeparately() {
        var activity = ScheduleActivity.create("A-1", "Deliver units", LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-10-10"), new WorkItemReference("DELIVERABLE", "item-1"), 0);
        var result = QuantityBasedProgressAssessment.assess(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                activity, LocalDate.parse("2026-10-05"), new BigDecimal("10"), new BigDecimal("12.5"));
        assertEquals(new BigDecimal("100.00"), result.completionPercent());
        assertEquals(new BigDecimal("2.500000"), result.overrunQuantity());
        assertEquals(ProgressAssessment.Status.COMPLETE, result.scheduleAssessment().status());
    }

    @Test void partialQuantityProducesConsistentAssessment() {
        var activity = ScheduleActivity.create("A-2", "Implement feature", LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-10-10"), new WorkItemReference("BACKLOG_ITEM", "feature-2"), 0);
        var result = QuantityBasedProgressAssessment.assess(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                activity, LocalDate.parse("2026-10-05"), new BigDecimal("20"), new BigDecimal("5"));
        assertEquals(new BigDecimal("25.00"), result.completionPercent());
        assertEquals(new BigDecimal("0.000000"), result.overrunQuantity());
        assertEquals(new BigDecimal("25.00"), result.scheduleAssessment().actualPercent());
    }

    @Test void requiresPositivePlannedQuantity() {
        var activity = ScheduleActivity.create("A-3", "Zero quantity", LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-10-10"), null, 0);
        assertThrows(IllegalArgumentException.class, () -> QuantityBasedProgressAssessment.assess(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), activity, LocalDate.parse("2026-10-05"), BigDecimal.ZERO, BigDecimal.ZERO));
    }
}
