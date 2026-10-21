package tech.kayys.numou.education.school.application.command;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.education.core.*;
import tech.kayys.numou.education.school.adapter.memory.*;
import tech.kayys.numou.education.school.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchoolPhase2HandlersTest {

    private final TenantId tenant = TenantId.generate();
    private final InMemorySchoolRepository schools = new InMemorySchoolRepository();
    private final InMemoryStudentRepository students = new InMemoryStudentRepository();
    private final InMemoryTeacherRepository teachers = new InMemoryTeacherRepository();
    private final InMemoryClassroomRepository classrooms = new InMemoryClassroomRepository();
    private final InMemoryLessonSlotRepository slots = new InMemoryLessonSlotRepository();
    private final InMemoryReportCardRepository reportCards = new InMemoryReportCardRepository();
    private final InMemoryFeeInvoiceRepository invoices = new InMemoryFeeInvoiceRepository();

    private SchoolId school() {
        return new RegisterSchoolHandler(schools).handle(new RegisterSchoolCommand(tenant, "20212345", "SMPN 1", EducationLevel.SMP,
                Curriculum.KURIKULUM_MERDEKA, SchoolOwnership.NEGERI)).toCompletableFuture().join().orElseThrow();
    }

    private StudentId student(SchoolId s, String nisn) {
        return new RegisterStudentHandler(schools, students).handle(new RegisterStudentCommand(tenant, s, "Siti", Gender.PEREMPUAN,
                LocalDate.of(2012, 5, 5), "Sumedang", Religion.ISLAM, nisn, "1")).toCompletableFuture().join().orElseThrow();
    }

    private ClassroomId classroom(SchoolId s, String name) {
        return new CreateClassroomHandler(schools, classrooms).handle(new CreateClassroomCommand(tenant, s, name,
                GradeLevel.of(EducationLevel.SMP, 7), AcademicYear.of(2025), 30, null)).toCompletableFuture().join().orElseThrow();
    }

    private TeacherId teacher(SchoolId s) {
        Teacher t = Teacher.register(TeacherId.generate(), tenant, s, "Bu Rina", Gender.PEREMPUAN, null, EmploymentType.PNS);
        teachers.save(t).toCompletableFuture().join();
        return t.getId();
    }

    private tech.kayys.numou.foundation.application.result.Result<LessonSlotId> schedule(ClassroomId c, TeacherId t, int sh, int eh) {
        return new ScheduleLessonHandler(classrooms, teachers, slots).handle(new ScheduleLessonCommand(tenant, c, t, "MTK",
                DayOfWeek.MONDAY, LocalTime.of(sh, 0), LocalTime.of(eh, 0), null)).toCompletableFuture().join();
    }

    @Test
    void timetable_blocksTeacherAndClassroomConflicts() {
        SchoolId s = school();
        ClassroomId a = classroom(s, "VII-A");
        ClassroomId b = classroom(s, "VII-B");
        TeacherId t1 = teacher(s);
        TeacherId t2 = teacher(s);
        assertThat(schedule(a, t1, 7, 8).isSuccess()).isTrue();
        assertThat(schedule(b, t1, 7, 8).failureError().get().code()).isEqualTo("school.timetable.teacher-conflict");
        assertThat(schedule(a, t2, 7, 8).failureError().get().code()).isEqualTo("school.timetable.classroom-conflict");
        assertThat(schedule(a, t1, 8, 9).isSuccess()).isTrue();
    }

    @Test
    void timetable_rejectsInactiveTeacher_andBadTimes() {
        SchoolId s = school();
        ClassroomId a = classroom(s, "VII-A");
        Teacher t = Teacher.register(TeacherId.generate(), tenant, s, "Pak Budi", Gender.LAKI_LAKI, null, EmploymentType.GTT);
        t.deactivate();
        teachers.save(t).toCompletableFuture().join();
        assertThat(schedule(a, t.getId(), 7, 8).failureError().get().code()).isEqualTo("school.teacher.inactive");
        assertThat(schedule(a, teacher(s), 9, 8).failureError().get().code()).isEqualTo("school.timetable.invalid");
    }

    @Test
    void reportCard_openRecordFinalizeFlow() {
        SchoolId s = school();
        ClassroomId c = classroom(s, "VII-A");
        StudentId st = student(s, "0123456789");
        new EnrollStudentHandler(classrooms, students).handle(new EnrollStudentCommand(tenant, c, st)).toCompletableFuture().join().orElseThrow();

        var open = new OpenReportCardHandler(classrooms, reportCards);
        ReportCardId id = open.handle(new OpenReportCardCommand(tenant, c, st, Semester.GANJIL)).toCompletableFuture().join().orElseThrow();
        assertThat(open.handle(new OpenReportCardCommand(tenant, c, st, Semester.GANJIL)).toCompletableFuture().join()
                .failureError().get().code()).isEqualTo("school.report-card.exists");
        assertThat(open.handle(new OpenReportCardCommand(tenant, c, st, Semester.GENAP)).toCompletableFuture().join().isSuccess()).isTrue();

        var finalizer = new FinalizeReportCardHandler(reportCards);
        assertThat(finalizer.handle(new FinalizeReportCardCommand(tenant, id)).toCompletableFuture().join()
                .failureError().get().code()).isEqualTo("school.report-card.cannot-finalize");

        var record = new RecordReportCardHandler(reportCards);
        assertThat(record.handle(new RecordReportCardCommand(tenant, id, List.of(new SubjectResult("MTK", Score.of(88), Score.of(90), "Baik")),
                1, 0, 0, "Pertahankan")).toCompletableFuture().join().isSuccess()).isTrue();
        assertThat(finalizer.handle(new FinalizeReportCardCommand(tenant, id)).toCompletableFuture().join().isSuccess()).isTrue();
        assertThat(record.handle(new RecordReportCardCommand(tenant, id, List.of(), 0, 0, 0, null)).toCompletableFuture().join()
                .failureError().get().code()).isEqualTo("school.report-card.cannot-edit");
    }

    @Test
    void reportCard_requiresRosteredStudent() {
        SchoolId s = school();
        var r = new OpenReportCardHandler(classrooms, reportCards).handle(new OpenReportCardCommand(tenant, classroom(s, "VII-A"),
                student(s, "0123456789"), Semester.GANJIL)).toCompletableFuture().join();
        assertThat(r.failureError().get().code()).isEqualTo("school.report-card.not-rostered");
    }

    @Test
    void fees_issueOncePerPeriod_thenPayInInstallments() {
        SchoolId s = school();
        StudentId st = student(s, "0123456789");
        var issue = new IssueFeeInvoiceHandler(students, invoices);
        FeeInvoiceId id = issue.handle(new IssueFeeInvoiceCommand(tenant, st, YearMonth.of(2025, 9), new BigDecimal("300000"), LocalDate.of(2025, 9, 10)))
                .toCompletableFuture().join().orElseThrow();
        assertThat(issue.handle(new IssueFeeInvoiceCommand(tenant, st, YearMonth.of(2025, 9), new BigDecimal("300000"), LocalDate.of(2025, 9, 10)))
                .toCompletableFuture().join().failureError().get().code()).isEqualTo("school.fee.duplicate-period");
        assertThat(issue.handle(new IssueFeeInvoiceCommand(tenant, st, YearMonth.of(2025, 10), new BigDecimal("300000"), LocalDate.of(2025, 10, 10)))
                .toCompletableFuture().join().isSuccess()).isTrue();

        var pay = new RecordFeePaymentHandler(invoices);
        assertThat(pay.handle(new RecordFeePaymentCommand(tenant, id, new BigDecimal("100000"), LocalDate.of(2025, 9, 2), "QRIS")).toCompletableFuture().join().isSuccess()).isTrue();
        assertThat(pay.handle(new RecordFeePaymentCommand(tenant, id, new BigDecimal("250000"), LocalDate.of(2025, 9, 3), "QRIS")).toCompletableFuture().join()
                .failureError().get().code()).isEqualTo("school.fee.cannot-pay");
        assertThat(invoices.findById(id).toCompletableFuture().join().get().getStatus()).isEqualTo(FeeStatus.PARTIAL);
    }

    @Test
    void fees_rejectInactiveStudent() {
        SchoolId s = school();
        StudentId st = student(s, "0123456789");
        Student stu = students.findById(st).toCompletableFuture().join().get();
        stu.dropOut();
        students.save(stu).toCompletableFuture().join();
        var r = new IssueFeeInvoiceHandler(students, invoices).handle(new IssueFeeInvoiceCommand(tenant, st, YearMonth.of(2025, 9),
                new BigDecimal("1000"), LocalDate.of(2025, 9, 10))).toCompletableFuture().join();
        assertThat(r.failureError().get().code()).isEqualTo("school.student.inactive");
    }
}
