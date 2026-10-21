package tech.kayys.numou.education.pesantren.domain;

import java.io.Serializable;

/** A contiguous ayah range inside one surah. */
public record QuranRange(int surah, int fromAyah, int toAyah) implements Serializable {
    public QuranRange {
        int max = QuranMeta.ayahCount(surah);
        if (fromAyah < 1 || toAyah < fromAyah || toAyah > max) {
            throw new IllegalArgumentException("Invalid ayah range " + fromAyah + "-" + toAyah
                    + " for surah " + surah + " (1-" + max + ")");
        }
    }

    public int ayahCount() { return toAyah - fromAyah + 1; }
}
