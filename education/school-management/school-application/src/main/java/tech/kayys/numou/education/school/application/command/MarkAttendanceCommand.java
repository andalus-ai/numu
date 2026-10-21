package tech.kayys.numou.education.school.application.command;

import tech.kayys.numou.education.core.ClassroomId;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.school.domain.AttendanceStatus;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.LocalDate;
import java.util.Map;

public record MarkAttendanceCommand(TenantId tenantId, ClassroomId classroomId, LocalDate date,
                                    Map<StudentId, AttendanceStatus> marks, boolean lockAfter) {}
