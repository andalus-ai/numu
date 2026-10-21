package tech.kayys.numou.education.core;

import java.util.Optional;

/**
 * Jenjang pendidikan. Madrasah levels (RA, MI, MTS, MA) fall under Kementerian Agama;
 * the others under the education ministry. Grade ranges follow the national numbering
 * (SD 1-6, SMP 7-9, SMA/MA 10-12).
 */
public enum EducationLevel {
    PAUD(1, 3, false),
    TK(1, 2, false),
    RA(1, 2, true),
    SD(1, 6, false),
    MI(1, 6, true),
    SMP(7, 9, false),
    MTS(7, 9, true),
    SMA(10, 12, false),
    SMK(10, 13, false),
    MA(10, 12, true);

    private final int minGrade;
    private final int maxGrade;
    private final boolean madrasah;

    EducationLevel(int minGrade, int maxGrade, boolean madrasah) {
        this.minGrade = minGrade;
        this.maxGrade = maxGrade;
        this.madrasah = madrasah;
    }

    public int minGrade() { return minGrade; }
    public int maxGrade() { return maxGrade; }
    public boolean isMadrasah() { return madrasah; }
    public boolean acceptsGrade(int grade) { return grade >= minGrade && grade <= maxGrade; }

    /** The general-school level that a madrasah level is equivalent to, if any. */
    public Optional<EducationLevel> generalEquivalent() {
        return switch (this) {
            case RA -> Optional.of(TK);
            case MI -> Optional.of(SD);
            case MTS -> Optional.of(SMP);
            case MA -> Optional.of(SMA);
            default -> Optional.empty();
        };
    }
}
