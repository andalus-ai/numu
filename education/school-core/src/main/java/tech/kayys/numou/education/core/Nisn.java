package tech.kayys.numou.education.core;

import java.io.Serializable;

/** Nomor Induk Siswa Nasional, 10-digit national student number. */
public record Nisn(String value) implements Serializable {
    public Nisn {
        value = Digits.require(value, 10, "NISN");
    }

    public static Nisn of(String value) { return new Nisn(value); }

    @Override
    public String toString() { return value; }
}
