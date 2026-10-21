package tech.kayys.numou.education.school.application.command;

import tech.kayys.numou.education.core.ClassroomId;
import tech.kayys.numou.education.core.Semester;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record OpenReportCardCommand(TenantId tenantId, ClassroomId classroomId, StudentId studentId, Semester semester) {}
