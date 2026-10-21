package tech.kayys.numou.education.school.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.education.core.*;
import tech.kayys.numou.education.school.domain.event.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchoolDomainTest {

    private static final TenantId TENANT = TenantId.generate();

    private static Student student() {
        return Student.register(StudentId.generate(), TENANT, SchoolId.generate(), "Siti Aisyah", Gender.PEREMPUAN,
                LocalDate.of(2012, 3, 4), "Sumedang", Religion.ISLAM, Nisn.of("0123456789"), "1001");
    }

    private static Classroom classroom(int capacity) {
        return Classroom.create(ClassroomId.generate(), TENANT, SchoolId.generate(), "VII-A",
                GradeLevel.of(EducationLevel.SMP, 7), AcademicYear.of(2025), capacity);
    }

    @Test
    void registerSchool_raisesEvent() {
        School s = School.register(SchoolId.generate(), TENANT, Npsn.of("20212345"), "SMPN 1 Sumedang",
                EducationLevel.SMP, Curriculum.KURIKULUM_MERDEKA, SchoolOwnership.NEGERI);
        assertThat(s.pullDomainEvents()).hasSize(1).first().isInstanceOf(SchoolRegistered.class);
    }

    @Test
    void changeCurriculum_isNoOpWhenSame() {
        School s = School.register(SchoolId.generate(), TENANT, Npsn.of("20212345"), "SMPN 1",
                EducationLevel.SMP, Curriculum.KURIKULUM_MERDEKA, SchoolOwnership.NEGERI);
        s.pullDomainEvents();
        s.changeCurriculum(Curriculum.KURIKULUM_MERDEKA);
        assertThat(s.pullDomainEvents()).isEmpty();
        s.changeCurriculum(Curriculum.KURIKULUM_2013);
        assertThat(s.pullDomainEvents()).hasSize(1).first().isInstanceOf(SchoolCurriculumChanged.class);
    }

    @Test
    void student_rejectsFutureBirthDate() {
        assertThatThrownBy(() -> Student.register(StudentId.generate(), TENANT, SchoolId.generate(), "X", Gender.LAKI_LAKI,
                LocalDate.now().plusDays(1), "Y", Religion.ISLAM, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("past");
    }

    @Test
    void student_statusTransitionsOnlyFromActive() {
        Student s = student();
        s.graduate();
        assertThat(s.getStatus()).isEqualTo(StudentStatus.GRADUATED);
        assertThatThrownBy(s::dropOut).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void student_nisnAssignedOnlyOnce() {
        Student s = Student.register(StudentId.generate(), TENANT, SchoolId.generate(), "Budi", Gender.LAKI_LAKI,
                LocalDate.of(2011, 1, 1), "Bandung", Religion.ISLAM, null, null);
        s.assignNisn(Nisn.of("1111111111"));
        assertThatThrownBy(() -> s.assignNisn(Nisn.of("2222222222"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void classroom_enforcesCapacityAndDuplicates() {
        Classroom c = classroom(1);
        StudentId a = StudentId.generate();
        c.enroll(a);
        assertThat(c.isFull()).isTrue();
        assertThatThrownBy(() -> c.enroll(StudentId.generate())).hasMessageContaining("full");
        assertThatThrownBy(() -> c.enroll(a)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void classroom_removeFreesSeat() {
        Classroom c = classroom(1);
        StudentId a = StudentId.generate();
        c.enroll(a);
        c.remove(a);
        assertThat(c.isFull()).isFalse();
        assertThat(c.hasStudent(a)).isFalse();
    }

    @Test
    void attendance_summaryAndRate() {
        AttendanceSheet sheet = AttendanceSheet.open(AttendanceSheetId.generate(), TENANT, ClassroomId.generate(), LocalDate.now());
        sheet.mark(StudentId.generate(), AttendanceStatus.HADIR);
        sheet.mark(StudentId.generate(), AttendanceStatus.HADIR);
        sheet.mark(StudentId.generate(), AttendanceStatus.SAKIT);
        sheet.mark(StudentId.generate(), AttendanceStatus.ALPA);
        assertThat(sheet.summary().get(AttendanceStatus.HADIR)).isEqualTo(2L);
        assertThat(sheet.attendanceRate()).isEqualTo(0.5);
    }

    @Test
    void attendance_lockedSheetIsImmutable() {
        AttendanceSheet sheet = AttendanceSheet.open(AttendanceSheetId.generate(), TENANT, ClassroomId.generate(), LocalDate.now());
        sheet.lock();
        assertThatThrownBy(() -> sheet.mark(StudentId.generate(), AttendanceStatus.HADIR)).hasMessageContaining("locked");
    }

    @Test
    void reportCard_finalizeRequiresSubjects_andFreezes() {
        ReportCard rc = ReportCard.draft(ReportCardId.generate(), TENANT, StudentId.generate(), ClassroomId.generate(),
                AcademicYear.of(2025), Semester.GANJIL);
        assertThatThrownBy(rc::finalizeCard).isInstanceOf(IllegalStateException.class);
        rc.recordSubject(new SubjectResult("MTK", Score.of(80), Score.of(90), "Baik"));
        rc.recordSubject(new SubjectResult("IPA", Score.of(90), Score.of(70), "Baik"));
        assertThat(rc.averageKnowledge().value().toPlainString()).isEqualTo("85.00");
        rc.finalizeCard();
        assertThat(rc.pullDomainEvents()).hasSize(1).first().isInstanceOf(ReportCardFinalized.class);
        assertThatThrownBy(() -> rc.recordSubject(new SubjectResult("IPS", Score.of(70), Score.of(70), "x")))
                .hasMessageContaining("finalized");
    }
}
