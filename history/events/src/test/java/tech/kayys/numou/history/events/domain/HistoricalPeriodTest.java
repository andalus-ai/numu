package tech.kayys.numou.history.events.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.history.chronology.domain.*;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalPeriodTest {
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final HistoricalChronology CHRONOLOGY = HistoricalChronology.standard();

    @Test
    void childPeriodMustFitInsideParent() {
        HistoricalPeriodId parentId = HistoricalPeriodId.generate();
        HistoricalPeriod parent = new HistoricalPeriod(parentId, "Common Era", "",
                new HistoricalDateRange(
                        HistoricalDate.year(1, GregorianCalendarDateConverter.ID),
                        HistoricalDate.year(2000, GregorianCalendarDateConverter.ID)),
                HistoricalCategoryCode.of("era"), null, 1, T0, T0);
        HistoricalPeriod child = HistoricalPeriod.create("Medieval period", "",
                new HistoricalDateRange(
                        HistoricalDate.year(500, GregorianCalendarDateConverter.ID),
                        HistoricalDate.year(1500, GregorianCalendarDateConverter.ID)),
                HistoricalCategoryCode.of("era"), parentId, T0);

        assertDoesNotThrow(() -> HistoricalPeriodHierarchy.assertChildWithinParent(parent, child, CHRONOLOGY));
    }

    @Test
    void childPeriodOutsideParentIsRejected() {
        HistoricalPeriodId parentId = HistoricalPeriodId.generate();
        HistoricalPeriod parent = new HistoricalPeriod(parentId, "Parent", "",
                new HistoricalDateRange(HistoricalDate.year(1000, GregorianCalendarDateConverter.ID),
                        HistoricalDate.year(1200, GregorianCalendarDateConverter.ID)),
                HistoricalCategoryCode.of("era"), null, 1, T0, T0);
        HistoricalPeriod child = HistoricalPeriod.create("Child", "",
                new HistoricalDateRange(HistoricalDate.year(1100, GregorianCalendarDateConverter.ID),
                        HistoricalDate.year(1300, GregorianCalendarDateConverter.ID)),
                HistoricalCategoryCode.of("dynasty"), parentId, T0);

        assertThrows(IllegalArgumentException.class,
                () -> HistoricalPeriodHierarchy.assertChildWithinParent(parent, child, CHRONOLOGY));
    }

    @Test
    void periodCanBeRevisedWithoutChangingItsIdentityOrRecordTime() {
        HistoricalPeriod period = HistoricalPeriod.create("Old name", "",
                new HistoricalDateRange(HistoricalDate.year(1000, GregorianCalendarDateConverter.ID),
                        HistoricalDate.year(1100, GregorianCalendarDateConverter.ID)),
                HistoricalCategoryCode.of("era"), null, T0);
        HistoricalPeriod revised = period.revise("New name", "Clarified dates", period.dateRange(),
                period.category(), null, T0.plusSeconds(60));

        assertEquals(period.id(), revised.id());
        assertEquals(T0, revised.recordedAt());
        assertEquals(2, revised.revision());
        assertEquals("New name", revised.name());
    }

    @Test
    void eventAndPeriodRelationNormalizesDifferentCalendars() {
        HistoricalDateRange eventRange = new HistoricalDateRange(
                HistoricalDate.day(1582, 10, 15, GregorianCalendarDateConverter.ID),
                HistoricalDate.day(1582, 10, 15, GregorianCalendarDateConverter.ID));
        HistoricalEvent event = HistoricalEvent.draft("Reform date", "", eventRange,
                HistoricalCategoryCode.of("political"), java.util.List.of(), T0);
        HistoricalPeriod period = HistoricalPeriod.create("Julian era segment", "",
                new HistoricalDateRange(
                        HistoricalDate.day(1582, 10, 5, JulianCalendarDateConverter.ID),
                        HistoricalDate.day(1582, 10, 5, JulianCalendarDateConverter.ID)),
                HistoricalCategoryCode.of("era"), null, T0);

        assertEquals(Optional.of(TemporalRelation.SAME),
                HistoricalEventPeriodRelation.relate(event, period, CHRONOLOGY));
    }
}
