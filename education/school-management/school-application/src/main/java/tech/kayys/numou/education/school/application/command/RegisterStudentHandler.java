package tech.kayys.numou.education.school.application.command;

import tech.kayys.numou.education.core.Nisn;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.school.domain.Student;
import tech.kayys.numou.education.school.spi.SchoolRepository;
import tech.kayys.numou.education.school.spi.StudentRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class RegisterStudentHandler {

    private final SchoolRepository schools;
    private final StudentRepository students;

    public RegisterStudentHandler(SchoolRepository schools, StudentRepository students) {
        this.schools = Objects.requireNonNull(schools);
        this.students = Objects.requireNonNull(students);
    }

    public CompletionStage<Result<StudentId>> handle(RegisterStudentCommand cmd) {
        Nisn nisn;
        try {
            nisn = cmd.nisn() == null ? null : Nisn.of(cmd.nisn());
        } catch (IllegalArgumentException e) {
            return Outcomes.failed("school.student.invalid-nisn", e.getMessage());
        }
        return schools.findById(cmd.schoolId()).thenCompose(school -> {
            if (school.isEmpty() || !school.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<StudentId>failed("school.not-found", "School not found");
            }
            CompletionStage<java.util.Optional<Student>> dup = nisn == null
                    ? CompletableFuture.completedFuture(java.util.Optional.empty())
                    : students.findByNisn(cmd.tenantId(), nisn);
            return dup.thenCompose(existing -> {
                if (existing.isPresent()) {
                    return Outcomes.<StudentId>failed("school.student.duplicate-nisn", "NISN is already registered");
                }
                Student student;
                try {
                    student = Student.register(StudentId.generate(), cmd.tenantId(), cmd.schoolId(), cmd.fullName(),
                            cmd.gender(), cmd.birthDate(), cmd.birthPlace(), cmd.religion(), nisn, cmd.nis());
                } catch (IllegalArgumentException | NullPointerException e) {
                    return Outcomes.<StudentId>failed("school.student.invalid", e.getMessage());
                }
                return students.save(student).thenApply(saved -> Result.success(saved.getId()));
            });
        });
    }
}
