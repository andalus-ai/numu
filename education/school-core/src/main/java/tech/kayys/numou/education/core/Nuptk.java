package tech.kayys.numou.education.core;

import java.io.Serializable;

/** Nomor Unik Pendidik dan Tenaga Kependidikan, 16-digit educator number. */
public record Nuptk(String value) implements Serializable {
    public Nuptk {
        value = Digits.require(value, 16, "NUPTK");
    }

    public static Nuptk of(String value) { return new Nuptk(value); }

    @Override
    public String toString() { return value; }
}
