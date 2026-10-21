package tech.kayys.numou.education.madrasah.domain;

import java.time.LocalDate;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;

/**
 * Hijri calendar helpers based on the Umm al-Qura calendar shipped with the JDK.
 * Indonesia's official month starts (e.g. 1 Ramadan, 1 Syawal) are set by Kemenag's sidang isbat
 * and can differ by a day, so use these for display and planning, not as the legal date.
 */
public final class HijriDates {

    private static final String[] MONTHS = {
            "Muharram", "Safar", "Rabiul Awal", "Rabiul Akhir", "Jumadil Awal", "Jumadil Akhir",
            "Rajab", "Syaban", "Ramadan", "Syawal", "Zulkaidah", "Zulhijah"
    };

    private HijriDates() {}

    public static HijrahDate of(LocalDate date) { return HijrahDate.from(date); }

    public static int month(LocalDate date) { return of(date).get(ChronoField.MONTH_OF_YEAR); }

    public static boolean isRamadan(LocalDate date) { return month(date) == 9; }

    /** e.g. "12 Ramadan 1447 H". */
    public static String format(LocalDate date) {
        HijrahDate h = of(date);
        return h.get(ChronoField.DAY_OF_MONTH) + " " + MONTHS[h.get(ChronoField.MONTH_OF_YEAR) - 1]
                + " " + h.get(ChronoField.YEAR) + " H";
    }
}
