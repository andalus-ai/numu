package tech.kayys.numou.education.core;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EducationCoreTest {

    @Test
    void nationalIdentifiers_validateLengthAndDigits() {
        assertThat(Nisn.of("0123456789").value()).isEqualTo("0123456789");
        assertThat(Npsn.of("20212345").value()).isEqualTo("20212345");
        assertThat(Nik.of("3211010101010001").value()).isEqualTo("3211010101010001");
        assertThatThrownBy(() -> Nisn.of("12345")).hasMessageContaining("10 digits");
        assertThatThrownBy(() -> Npsn.of("2021234A")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Nuptk.of("123")).hasMessageContaining("16 digits");
    }

    @Test
    void academicYear_startsInJuly() {
        assertThat(AcademicYear.containing(LocalDate.of(2025, 7, 1))).isEqualTo(AcademicYear.of(2025));
        assertThat(AcademicYear.containing(LocalDate.of(2026, 6, 30))).isEqualTo(AcademicYear.of(2025));
        assertThat(AcademicYear.of(2025).label()).isEqualTo("2025/2026");
        assertThat(AcademicYear.of(2025).contains(LocalDate.of(2026, 1, 15))).isTrue();
        assertThat(AcademicYear.of(2025).contains(LocalDate.of(2026, 7, 1))).isFalse();
    }

    @Test
    void academicYear_parse() {
        assertThat(AcademicYear.parse("2025/2026")).isEqualTo(AcademicYear.of(2025));
        assertThatThrownBy(() -> AcademicYear.parse("2025/2027")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void semester_followsTheSchoolCalendar() {
        assertThat(Semester.of(LocalDate.of(2025, 9, 1))).isEqualTo(Semester.GANJIL);
        assertThat(Semester.of(LocalDate.of(2026, 2, 1))).isEqualTo(Semester.GENAP);
    }

    @Test
    void gradeLevel_validatesAgainstLevel() {
        assertThat(GradeLevel.of(EducationLevel.SMP, 9).toString()).isEqualTo("SMP 9");
        assertThatThrownBy(() -> GradeLevel.of(EducationLevel.SD, 7)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GradeLevel.of(EducationLevel.MA, 9)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void madrasahLevels_mapToGeneralEquivalents() {
        assertThat(EducationLevel.MI.isMadrasah()).isTrue();
        assertThat(EducationLevel.SD.isMadrasah()).isFalse();
        assertThat(EducationLevel.MTS.generalEquivalent().get()).isEqualTo(EducationLevel.SMP);
        assertThat(EducationLevel.SMK.generalEquivalent().isPresent()).isFalse();
    }

    @Test
    void score_roundsAndValidates() {
        assertThat(Score.of("85.456").toString()).isEqualTo("85.46");
        assertThatThrownBy(() -> Score.of(100.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Score.of(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void score_predikatUsesConfigurableBands() {
        assertThat(Score.of(92).predikat(ScoreBands.DEFAULT)).isEqualTo(Predikat.A);
        assertThat(Score.of(80).predikat(ScoreBands.DEFAULT)).isEqualTo(Predikat.B);
        assertThat(Score.of(69.99).predikat(ScoreBands.DEFAULT)).isEqualTo(Predikat.D);
        ScoreBands strict = new ScoreBands(new java.math.BigDecimal("95"), new java.math.BigDecimal("85"), new java.math.BigDecimal("75"));
        assertThat(Score.of(92).predikat(strict)).isEqualTo(Predikat.B);
        assertThatThrownBy(() -> new ScoreBands(new java.math.BigDecimal("70"), new java.math.BigDecimal("80"), new java.math.BigDecimal("90")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
