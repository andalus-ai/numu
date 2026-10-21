package tech.kayys.numou.education.core;

import java.io.Serializable;
import java.time.LocalDate;

/** Tahun ajaran, e.g. 2025/2026. The Indonesian school year starts in July. */
public record AcademicYear(int startYear) implements Serializable {
    public AcademicYear {
        if (startYear < 1900 || startYear > 2200) {
            throw new IllegalArgumentException("startYear out of range: " + startYear);
        }
    }

    public static AcademicYear of(int startYear) { return new AcademicYear(startYear); }

    public static AcademicYear containing(LocalDate date) {
        return new AcademicYear(date.getMonthValue() >= 7 ? date.getYear() : date.getYear() - 1);
    }

    public static AcademicYear parse(String label) {
        String[] p = label.trim().split("/");
        if (p.length != 2) throw new IllegalArgumentException("Expected format YYYY/YYYY: " + label);
        int start = Integer.parseInt(p[0]);
        if (Integer.parseInt(p[1]) != start + 1) {
            throw new IllegalArgumentException("Academic year must span consecutive years: " + label);
        }
        return new AcademicYear(start);
    }

    public int endYear() { return startYear + 1; }

    public LocalDate startDate() { return LocalDate.of(startYear, 7, 1); }

    public LocalDate endDate() { return LocalDate.of(endYear(), 6, 30); }

    public boolean contains(LocalDate d) { return !d.isBefore(startDate()) && !d.isAfter(endDate()); }

    public String label() { return startYear + "/" + endYear(); }

    @Override
    public String toString() { return label(); }
}
