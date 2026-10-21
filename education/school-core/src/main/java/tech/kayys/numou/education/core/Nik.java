package tech.kayys.numou.education.core;

import java.io.Serializable;

/** Nomor Induk Kependudukan, 16-digit national population number. */
public record Nik(String value) implements Serializable {
    public Nik {
        value = Digits.require(value, 16, "NIK");
    }

    public static Nik of(String value) { return new Nik(value); }

    @Override
    public String toString() { return value; }
}
