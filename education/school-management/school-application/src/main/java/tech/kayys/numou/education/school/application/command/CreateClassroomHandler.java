package tech.kayys.numou.education.school.application.command;

import tech.kayys.numou.education.core.ClassroomId;
import tech.kayys.numou.education.school.domain.Classroom;
import tech.kayys.numou.education.school.spi.ClassroomRepository;
import tech.kayys.numou.education.school.spi.SchoolRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class CreateClassroomHandler {

    private final SchoolRepository schools;
    private final ClassroomRepository classrooms;

    public CreateClassroomHandler(SchoolRepository schools, ClassroomRepository classrooms) {
        this.schools = Objects.requireNonNull(schools);
        this.classrooms = Objects.requireNonNull(classrooms);
    }

    public CompletionStage<Result<ClassroomId>> handle(CreateClassroomCommand cmd) {
        return schools.findById(cmd.schoolId()).thenCompose(school -> {
            if (school.isEmpty() || !school.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<ClassroomId>failed("school.not-found", "School not found");
            }
            Classroom classroom;
            try {
                classroom = Classroom.create(ClassroomId.generate(), cmd.tenantId(), cmd.schoolId(), cmd.name(),
                        cmd.gradeLevel(), cmd.academicYear(), cmd.capacity());
                if (cmd.homeroomTeacher() != null) classroom.assignHomeroomTeacher(cmd.homeroomTeacher());
            } catch (IllegalArgumentException | NullPointerException e) {
                return Outcomes.<ClassroomId>failed("school.classroom.invalid", e.getMessage());
            }
            return classrooms.save(classroom).thenApply(saved -> Result.success(saved.getId()));
        });
    }
}
