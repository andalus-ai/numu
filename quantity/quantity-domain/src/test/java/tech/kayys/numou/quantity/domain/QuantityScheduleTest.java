package tech.kayys.numou.quantity.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QuantityScheduleTest {
    private static final UUID NODE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private QuantityScheduleLine line(String code, String unit, String value, int position) {
        return new QuantityScheduleLine(
                UUID.randomUUID(), NODE_ID, code, code,
                new Quantity(new BigDecimal(value), new UnitOfMeasure(unit)), position);
    }

    @Test
    void sortsLinesAndKeepsReturnedCollectionImmutable() {
        QuantityScheduleLine later = line("Q-02", "m3", "2", 1);
        QuantityScheduleLine earlier = line("Q-01", "m3", "1", 0);
        QuantitySchedule schedule = new QuantitySchedule(UUID.randomUUID(), List.of(later, earlier));

        assertEquals(List.of(earlier, later), schedule.lines());
        assertThrows(UnsupportedOperationException.class, () -> schedule.lines().clear());
    }

    @Test
    void rejectsDuplicateLineIdsCodesAndPositions() {
        QuantityScheduleLine first = line("Q-01", "m3", "1", 0);
        QuantityScheduleLine duplicateId = new QuantityScheduleLine(
                first.id(), NODE_ID, "Q-02", "second",
                new Quantity(BigDecimal.ONE, new UnitOfMeasure("m3")), 1);
        assertThrows(IllegalArgumentException.class,
                () -> new QuantitySchedule(UUID.randomUUID(), List.of(first, duplicateId)));

        QuantityScheduleLine duplicateCode = line("q-01", "m3", "1", 1);
        assertThrows(IllegalArgumentException.class,
                () -> new QuantitySchedule(UUID.randomUUID(), List.of(first, duplicateCode)));

        QuantityScheduleLine duplicatePosition = line("Q-03", "m3", "1", 0);
        assertThrows(IllegalArgumentException.class,
                () -> new QuantitySchedule(UUID.randomUUID(), List.of(first, duplicatePosition)));
    }

    @Test
    void addAndRemoveReturnNewSchedulesWithoutMutatingOriginal() {
        QuantityScheduleLine first = line("Q-01", "m3", "1.25", 0);
        QuantitySchedule original = new QuantitySchedule(UUID.randomUUID(), List.of(first));
        QuantityScheduleLine second = line("Q-02", "m3", "2.75", 1);

        QuantitySchedule withSecond = original.addLine(second);
        assertEquals(1, original.lines().size());
        assertEquals(2, withSecond.lines().size());
        assertEquals(1, withSecond.removeLine(first.id()).lines().size());
        assertThrows(IllegalArgumentException.class, () -> original.removeLine(UUID.randomUUID()));
    }

    @Test
    void totalsOnlyCombineMatchingNormalizedUnits() {
        QuantitySchedule schedule = new QuantitySchedule(UUID.randomUUID(), List.of(
                line("Q-01", " M3 ", "1.25", 0),
                line("Q-02", "m3", "2.75", 1),
                line("Q-03", "kg", "5", 2)));

        assertEquals(new BigDecimal("4.00"), schedule.total(new UnitOfMeasure("m3")).value());
        assertEquals(new BigDecimal("0"), schedule.total(new UnitOfMeasure("L")).value());

        Map<UnitOfMeasure, Quantity> totals = schedule.totalsByUnit();
        assertEquals(new BigDecimal("4.00"), totals.get(new UnitOfMeasure("m3")).value());
        assertEquals(new BigDecimal("5"), totals.get(new UnitOfMeasure("kg")).value());
        assertThrows(UnsupportedOperationException.class,
                () -> totals.put(new UnitOfMeasure("l"), new Quantity(BigDecimal.ONE, new UnitOfMeasure("l"))));
    }

    @Test
    void emptyScheduleHasNoTotalsUntilQueried() {
        QuantitySchedule schedule = QuantitySchedule.empty(UUID.randomUUID());

        assertTrue(schedule.lines().isEmpty());
        assertEquals(BigDecimal.ZERO, schedule.total(new UnitOfMeasure("m")).value());
        assertTrue(schedule.totalsByUnit().isEmpty());
    }
}
