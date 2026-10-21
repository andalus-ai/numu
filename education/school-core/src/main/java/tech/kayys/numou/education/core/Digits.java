package tech.kayys.numou.education.core;

import java.util.Objects;

/** Shared validation for numeric national identifiers (NISN, NPSN, NIK, NUPTK ...). */
final class Digits {
    private Digits() {}

    static String require(String value, int length, String label) {
        Objects.requireNonNull(value, label + " must not be null");
        String v = value.trim();
        if (v.length() != length || !v.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new IllegalArgumentException(label + " must be exactly " + length + " digits");
        }
        return v;
    }
}
