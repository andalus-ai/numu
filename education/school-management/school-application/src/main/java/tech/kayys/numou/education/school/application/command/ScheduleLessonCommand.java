package tech.kayys.numou.education.school.application.command;

import tech.kayys.numou.education.core.ClassroomId;
import tech.kayys.numou.education.core.TeacherId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record ScheduleLessonCommand(TenantId tenantId, ClassroomId classroomId, TeacherId teacherId, String subjectCode,
                                    DayOfWeek day, LocalTime start, LocalTime end, String room) {}
