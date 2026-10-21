package tech.kayys.numou.education.school.application.command;

import tech.kayys.numou.education.core.ClassroomId;
import tech.kayys.numou.education.school.spi.ClassroomRepository;
import tech.kayys.numou.education.school.spi.StudentRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Enrols an active student into a classroom. A student may be in only one classroom per academic year.
 */
public class EnrollStudentHandler {

    private final ClassroomRepository classrooms;
    private final StudentRepository students;

    public EnrollStudentHandler(ClassroomRepository classrooms, StudentRepository students) {
        this.classrooms = Objects.requireNonNull(classrooms);
        this.students = Objects.requireNonNull(students);
    }

    public CompletionStage<Result<ClassroomId>> handle(EnrollStudentCommand cmd) {
        return classrooms.findById(cmd.classroomId()).thenCompose(classroomOpt -> {
            if (classroomOpt.isEmpty() || !classroomOpt.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<ClassroomId>failed("school.classroom.not-found", "Classroom not found");
            }
            var classroom = classroomOpt.get();
            return students.findById(cmd.studentId()).thenCompose(studentOpt -> {
                if (studentOpt.isEmpty() || !studentOpt.get().getTenantId().equals(cmd.tenantId())) {
                    return Outcomes.<ClassroomId>failed("school.student.not-found", "Student not found");
                }
                var student = studentOpt.get();
                if (!student.isActive()) {
                    return Outcomes.<ClassroomId>failed("school.student.inactive", "Student is not active");
                }
                if (!student.getSchoolId().equals(classroom.getSchoolId())) {
                    return Outcomes.<ClassroomId>failed("school.student.other-school", "Student belongs to a different school");
                }
                return classrooms.findByStudentAndYear(cmd.tenantId(), cmd.studentId(), classroom.getAcademicYear())
                        .thenCompose(existing -> {
                            if (existing.isPresent()) {
                                return Outcomes.<ClassroomId>failed("school.student.already-in-classroom",
                                        "Student is already in classroom " + existing.get().getName()
                                                + " for " + classroom.getAcademicYear());
                            }
                            try {
                                classroom.enroll(cmd.studentId());
                            } catch (IllegalStateException e) {
                                return Outcomes.<ClassroomId>failed("school.classroom.cannot-enroll", e.getMessage());
                            }
                            return classrooms.save(classroom).thenApply(saved -> Result.success(saved.getId()));
                        });
            });
        });
    }
}
