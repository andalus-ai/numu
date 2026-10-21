package tech.kayys.numou.education.school.application.command;

import tech.kayys.numou.education.school.domain.LessonSlot;
import tech.kayys.numou.education.school.domain.LessonSlotId;
import tech.kayys.numou.education.school.spi.ClassroomRepository;
import tech.kayys.numou.education.school.spi.LessonSlotRepository;
import tech.kayys.numou.education.school.spi.TeacherRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Adds a timetable slot, refusing double-booked teachers and classrooms. */
public class ScheduleLessonHandler {

    private final ClassroomRepository classrooms;
    private final TeacherRepository teachers;
    private final LessonSlotRepository slots;

    public ScheduleLessonHandler(ClassroomRepository classrooms, TeacherRepository teachers, LessonSlotRepository slots) {
        this.classrooms = Objects.requireNonNull(classrooms);
        this.teachers = Objects.requireNonNull(teachers);
        this.slots = Objects.requireNonNull(slots);
    }

    public CompletionStage<Result<LessonSlotId>> handle(ScheduleLessonCommand cmd) {
        return classrooms.findById(cmd.classroomId()).thenCompose(cOpt -> {
            if (cOpt.isEmpty() || !cOpt.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<LessonSlotId>failed("school.classroom.not-found", "Classroom not found");
            }
            return teachers.findById(cmd.teacherId()).thenCompose(tOpt -> {
                if (tOpt.isEmpty() || !tOpt.get().getTenantId().equals(cmd.tenantId())) {
                    return Outcomes.<LessonSlotId>failed("school.teacher.not-found", "Teacher not found");
                }
                if (!tOpt.get().isActive()) return Outcomes.<LessonSlotId>failed("school.teacher.inactive", "Teacher is not active");
                LessonSlot slot;
                try {
                    slot = LessonSlot.schedule(LessonSlotId.generate(), cmd.tenantId(), cmd.classroomId(), cmd.teacherId(),
                            cmd.subjectCode(), cmd.day(), cmd.start(), cmd.end(), cmd.room());
                } catch (IllegalArgumentException | NullPointerException e) {
                    return Outcomes.<LessonSlotId>failed("school.timetable.invalid", e.getMessage());
                }
                return slots.findByTeacher(cmd.tenantId(), cmd.teacherId()).thenCompose(teacherSlots -> {
                    if (teacherSlots.stream().anyMatch(slot::overlaps)) {
                        return Outcomes.<LessonSlotId>failed("school.timetable.teacher-conflict", "Teacher already teaches at that time");
                    }
                    return slots.findByClassroom(cmd.classroomId()).thenCompose(classSlots -> {
                        if (classSlots.stream().anyMatch(slot::overlaps)) {
                            return Outcomes.<LessonSlotId>failed("school.timetable.classroom-conflict", "Classroom already has a lesson at that time");
                        }
                        return slots.save(slot).thenApply(saved -> Result.success(saved.getId()));
                    });
                });
            });
        });
    }
}
