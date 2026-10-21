package tech.kayys.numou.education.school.application.command;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.education.core.*;
import tech.kayys.numou.education.school.adapter.memory.*;
import tech.kayys.numou.education.school.domain.AttendanceStatus;
import tech.kayys.numou.education.school.domain.SchoolOwnership;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SchoolHandlersTest {

    private final TenantId tenant = TenantId.generate();
    private final InMemorySchoolRepository schools = new InMemorySchoolRepository();
    private final InMemoryStudentRepository students = new InMemoryStudentRepository();
    private final InMemoryClassroomRepository classrooms = new InMemoryClassroomRepository();
    private final InMemoryAttendanceSheetRepository sheets = new InMemoryAttendanceSheetRepository();

    private SchoolId registerSchool() {
        return new RegisterSchoolHandler(schools).handle(new RegisterSchoolCommand(tenant, "20212345", "SMPN 1",
                EducationLevel.SMP, Curriculum.KURIKULUM_MERDEKA, SchoolOwnership.NEGERI)).toCompletableFuture().join().orElseThrow();
    }

    private Result<StudentId> registerStudent(SchoolId school, String nisn) {
        return new RegisterStudentHandler(schools, students).handle(new RegisterStudentCommand(tenant, school, "Siti",
                Gender.PEREMPUAN, LocalDate.of(2012, 5, 5), "Sumedang", Religion.ISLAM, nisn, "100")).toCompletableFuture().join();
    }

    private ClassroomId createClassroom(SchoolId school, int capacity, int year) {
        return new CreateClassroomHandler(schools, classrooms).handle(new CreateClassroomCommand(tenant, school, "VII-A",
                GradeLevel.of(EducationLevel.SMP, 7), AcademicYear.of(year), capacity, null)).toCompletableFuture().join().orElseThrow();
    }

    private Result<ClassroomId> enroll(ClassroomId c, StudentId s) {
        return new EnrollStudentHandler(classrooms, students).handle(new EnrollStudentCommand(tenant, c, s)).toCompletableFuture().join();
    }

    @Test
    void registerSchool_rejectsBadNpsn() {
        Result<SchoolId> r = new RegisterSchoolHandler(schools).handle(new RegisterSchoolCommand(tenant, "123", "X",
                EducationLevel.SD, Curriculum.KTSP, SchoolOwnership.SWASTA)).toCompletableFuture().join();
        assertThat(r.isFailure()).isTrue();
        assertThat(r.failureError().get().code()).isEqualTo("school.invalid");
    }

    @Test
    void registerStudent_rejectsDuplicateNisn() {
        SchoolId school = registerSchool();
        assertThat(registerStudent(school, "0123456789").isSuccess()).isTrue();
        Result<StudentId> dup = registerStudent(school, "0123456789");
        assertThat(dup.failureError().get().code()).isEqualTo("school.student.duplicate-nisn");
    }

    @Test
    void registerStudent_allowsMissingNisn_andRejectsUnknownSchool() {
        assertThat(registerStudent(registerSchool(), null).isSuccess()).isTrue();
        assertThat(registerStudent(SchoolId.generate(), null).failureError().get().code()).isEqualTo("school.not-found");
    }

    @Test
    void enroll_succeedsOnce_thenBlocksSecondClassInSameYear() {
        SchoolId school = registerSchool();
        StudentId s = registerStudent(school, "0123456789").orElseThrow();
        ClassroomId a = createClassroom(school, 30, 2025);
        ClassroomId b = createClassroom(school, 30, 2025);
        ClassroomId nextYear = createClassroom(school, 30, 2026);
        assertThat(enroll(a, s).isSuccess()).isTrue();
        assertThat(enroll(b, s).failureError().get().code()).isEqualTo("school.student.already-in-classroom");
        assertThat(enroll(nextYear, s).isSuccess()).isTrue();
    }

    @Test
    void enroll_failsWhenClassroomFull() {
        SchoolId school = registerSchool();
        ClassroomId c = createClassroom(school, 1, 2025);
        StudentId s1 = registerStudent(school, "1111111111").orElseThrow();
        StudentId s2 = registerStudent(school, "2222222222").orElseThrow();
        assertThat(enroll(c, s1).isSuccess()).isTrue();
        assertThat(enroll(c, s2).failureError().get().code()).isEqualTo("school.classroom.cannot-enroll");
    }

    @Test
    void enroll_rejectsStudentFromOtherSchool() {
        SchoolId s1 = registerSchool();
        SchoolId s2 = registerSchool();
        StudentId student = registerStudent(s2, "3333333333").orElseThrow();
        assertThat(enroll(createClassroom(s1, 10, 2025), student).failureError().get().code()).isEqualTo("school.student.other-school");
    }

    @Test
    void attendance_reusesSheetPerDay_andRejectsOutsiders() {
        SchoolId school = registerSchool();
        ClassroomId c = createClassroom(school, 30, 2025);
        StudentId s = registerStudent(school, "4444444444").orElseThrow();
        enroll(c, s);
        var handler = new MarkAttendanceHandler(classrooms, sheets);
        LocalDate day = LocalDate.of(2025, 8, 18);

        var first = handler.handle(new MarkAttendanceCommand(tenant, c, day, Map.of(s, AttendanceStatus.HADIR), false)).toCompletableFuture().join();
        var second = handler.handle(new MarkAttendanceCommand(tenant, c, day, Map.of(s, AttendanceStatus.SAKIT), true)).toCompletableFuture().join();
        assertThat(second.orElseThrow()).isEqualTo(first.orElseThrow());

        var locked = handler.handle(new MarkAttendanceCommand(tenant, c, day, Map.of(s, AttendanceStatus.HADIR), false)).toCompletableFuture().join();
        assertThat(locked.failureError().get().code()).isEqualTo("school.attendance.locked");

        var outsider = handler.handle(new MarkAttendanceCommand(tenant, c, day.plusDays(1),
                Map.of(StudentId.generate(), AttendanceStatus.HADIR), false)).toCompletableFuture().join();
        assertThat(outsider.failureError().get().code()).isEqualTo("school.attendance.not-rostered");
    }
}
