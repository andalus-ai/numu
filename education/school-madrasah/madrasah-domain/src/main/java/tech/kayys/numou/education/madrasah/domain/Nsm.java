package tech.kayys.numou.education.madrasah.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * Nomor Statistik Madrasah, the Kemenag identifier of a madrasah (12 digits).
 * Confirm the length against your EMIS data before relying on it for integration.
 */
public record Nsm(String value) implements Serializable {
    public Nsm {
        Objects.requireNonNull(value, "NSM must not be null");
        value = value.trim();
        if (value.length() != 12 || !value.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new IllegalArgumentException("NSM must be exactly 12 digits");
        }
    }

    public static Nsm of(String value) { return new Nsm(value); }

    @Override
    public String toString() { return value; }
}
