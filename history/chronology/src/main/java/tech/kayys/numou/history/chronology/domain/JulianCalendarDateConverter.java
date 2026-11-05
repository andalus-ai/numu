package tech.kayys.numou.history.chronology.domain;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Proleptic Julian calendar adapter using astronomical year numbering. */
public final class JulianCalendarDateConverter implements CalendarDateConverter {
    public static final CalendarSystemId ID = CalendarSystemId.of("julian");

    @Override
    public CalendarSystemId calendarSystem() {
        return ID;
    }

    @Override
    public OptionalLong toAbsoluteDay(HistoricalDate date) {
        Objects.requireNonNull(date, "Date is required");
        requireCalendar(date);
        if (date.precision() != DatePrecision.DAY) return OptionalLong.empty();
        return OptionalLong.of(toDayNumber(date.astronomicalYear(), date.month(), date.day()));
    }

    @Override
    public Optional<AbsoluteDayRange> toAbsoluteDayBounds(HistoricalDate date) {
        Objects.requireNonNull(date, "Date is required");
        requireCalendar(date);
        int firstMonth = date.precision() == DatePrecision.YEAR ? 1 : date.month();
        int lastMonth = date.precision() == DatePrecision.YEAR ? 12 : date.month();
        int firstDay = date.precision() == DatePrecision.DAY ? date.day() : 1;
        int lastDay = date.precision() == DatePrecision.DAY ? date.day()
                : daysInMonth(date.astronomicalYear(), lastMonth);
        return Optional.of(new AbsoluteDayRange(
                toDayNumber(date.astronomicalYear(), firstMonth, firstDay),
                toDayNumber(date.astronomicalYear(), lastMonth, lastDay)));
    }

    static long toDayNumber(long year, int month, int day) {
        GregorianCalendarDateConverter.validateDate(year, month, day);
        if (month < 1 || month > 12 || day > daysInMonth(year, month)) {
            throw new IllegalArgumentException("Invalid Julian date");
        }
        BigInteger y = BigInteger.valueOf(year);
        BigInteger m = BigInteger.valueOf(month);
        BigInteger a = GregorianCalendarDateConverter.floorDivide(
                BigInteger.valueOf(14).subtract(m), BigInteger.valueOf(12));
        y = y.add(BigInteger.valueOf(4800)).subtract(a);
        BigInteger adjustedMonth = m.add(BigInteger.valueOf(12).multiply(a)).subtract(BigInteger.valueOf(3));
        BigInteger result = BigInteger.valueOf(day)
                .add(GregorianCalendarDateConverter.floorDivide(
                        BigInteger.valueOf(153).multiply(adjustedMonth).add(BigInteger.TWO), BigInteger.valueOf(5)))
                .add(BigInteger.valueOf(365).multiply(y))
                .add(GregorianCalendarDateConverter.floorDivide(y, BigInteger.valueOf(4)))
                .subtract(BigInteger.valueOf(32083));
        return result.longValueExact();
    }

    static int daysInMonth(long year, int month) {
        return switch (month) {
            case 1, 3, 5, 7, 8, 10, 12 -> 31;
            case 4, 6, 9, 11 -> 30;
            case 2 -> Math.floorMod(year, 4) == 0 ? 29 : 28;
            default -> throw new IllegalArgumentException("Julian month must be between 1 and 12");
        };
    }

    private static void requireCalendar(HistoricalDate date) {
        if (!ID.equals(date.calendarSystem())) {
            throw new IllegalArgumentException("Expected Julian date but received " + date.calendarSystem().value());
        }
    }
}
