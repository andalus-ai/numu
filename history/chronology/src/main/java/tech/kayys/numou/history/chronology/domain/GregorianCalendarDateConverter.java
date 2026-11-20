package tech.kayys.numou.history.chronology.domain;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Proleptic Gregorian calendar adapter using astronomical year numbering. */
public final class GregorianCalendarDateConverter implements CalendarDateConverter {
    public static final CalendarSystemId ID = CalendarSystemId.of("gregorian");
    private static final BigInteger FOUR = BigInteger.valueOf(4);
    private static final BigInteger ONE_HUNDRED = BigInteger.valueOf(100);
    private static final BigInteger FOUR_HUNDRED = BigInteger.valueOf(400);
    private static final BigInteger JDN_OFFSET = BigInteger.valueOf(32045);

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
    public HistoricalDate fromAbsoluteDay(long absoluteDay) {
        BigInteger jdn = BigInteger.valueOf(absoluteDay);
        BigInteger a = jdn.add(BigInteger.valueOf(32044));
        BigInteger b = floorDivide(BigInteger.valueOf(4).multiply(a).add(BigInteger.valueOf(3)),
                BigInteger.valueOf(146097));
        BigInteger c = a.subtract(floorDivide(BigInteger.valueOf(146097).multiply(b), BigInteger.valueOf(4)));
        BigInteger d = floorDivide(BigInteger.valueOf(4).multiply(c).add(BigInteger.valueOf(3)),
                BigInteger.valueOf(1461));
        BigInteger e = c.subtract(floorDivide(BigInteger.valueOf(1461).multiply(d), BigInteger.valueOf(4)));
        BigInteger m = floorDivide(BigInteger.valueOf(5).multiply(e).add(BigInteger.valueOf(2)),
                BigInteger.valueOf(153));
        BigInteger day = e.subtract(floorDivide(BigInteger.valueOf(153).multiply(m).add(BigInteger.valueOf(2)),
                BigInteger.valueOf(5))).add(BigInteger.ONE);
        BigInteger month = m.add(BigInteger.valueOf(3))
                .subtract(BigInteger.valueOf(12).multiply(floorDivide(m, BigInteger.TEN)));
        BigInteger year = BigInteger.valueOf(100).multiply(b).add(d).subtract(BigInteger.valueOf(4800))
                .add(floorDivide(m, BigInteger.TEN));
        return HistoricalDate.day(year.longValueExact(), month.intValueExact(), day.intValueExact(), ID);
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
        long first = toDayNumber(date.astronomicalYear(), firstMonth, firstDay);
        long last = toDayNumber(date.astronomicalYear(), lastMonth, lastDay);
        return Optional.of(new AbsoluteDayRange(first, last));
    }

    static long toDayNumber(long year, int month, int day) {
        validateDate(year, month, day);
        BigInteger y = BigInteger.valueOf(year);
        BigInteger m = BigInteger.valueOf(month);
        BigInteger a = floorDivide(BigInteger.valueOf(14).subtract(m), BigInteger.valueOf(12));
        y = y.add(BigInteger.valueOf(4800)).subtract(a);
        BigInteger adjustedMonth = m.add(BigInteger.valueOf(12).multiply(a)).subtract(BigInteger.valueOf(3));
        BigInteger result = BigInteger.valueOf(day)
                .add(floorDivide(BigInteger.valueOf(153).multiply(adjustedMonth).add(BigInteger.TWO), BigInteger.valueOf(5)))
                .add(BigInteger.valueOf(365).multiply(y))
                .add(floorDivide(y, FOUR))
                .subtract(floorDivide(y, ONE_HUNDRED))
                .add(floorDivide(y, FOUR_HUNDRED))
                .subtract(JDN_OFFSET);
        return result.longValueExact();
    }

    static void validateDate(long year, int month, int day) {
        if (month < 1 || month > 12) throw new IllegalArgumentException("Gregorian month must be between 1 and 12");
        int maximum = daysInMonth(year, month);
        if (day < 1 || day > maximum) {
            throw new IllegalArgumentException("Invalid Gregorian day " + day + " for month " + month);
        }
    }

    static int daysInMonth(long year, int month) {
        return switch (month) {
            case 1, 3, 5, 7, 8, 10, 12 -> 31;
            case 4, 6, 9, 11 -> 30;
            case 2 -> isLeapYear(year) ? 29 : 28;
            default -> throw new IllegalArgumentException("Gregorian month must be between 1 and 12");
        };
    }

    static boolean isLeapYear(long year) {
        return Math.floorMod(year, 4) == 0
                && (Math.floorMod(year, 100) != 0 || Math.floorMod(year, 400) == 0);
    }

    static BigInteger floorDivide(BigInteger dividend, BigInteger divisor) {
        BigInteger[] qr = dividend.divideAndRemainder(divisor);
        return qr[1].signum() < 0 ? qr[0].subtract(BigInteger.ONE) : qr[0];
    }

    private static void requireCalendar(HistoricalDate date) {
        if (!ID.equals(date.calendarSystem())) {
            throw new IllegalArgumentException("Expected Gregorian date but received " + date.calendarSystem().value());
        }
    }

}
