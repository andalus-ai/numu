package tech.kayys.numou.education.core;

import java.io.Serializable;

/** Nomor Pokok Sekolah Nasional, 8-digit national school number. */
public record Npsn(String value) implements Serializable {
    public Npsn {
        value = Digits.require(value, 8, "NPSN");
    }

    public static Npsn of(String value) { return new Npsn(value); }

    @Override
    public String toString() { return value; }
}
