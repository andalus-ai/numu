package tech.kayys.numou.education.core;

import java.time.LocalDate;

public enum Semester {
    GANJIL, // July - December
    GENAP;  // January - June

    public static Semester of(LocalDate date) {
        return date.getMonthValue() >= 7 ? GANJIL : GENAP;
    }
}
