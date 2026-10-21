package tech.kayys.numou.education.lms.application.command;

import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.lms.domain.CourseId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record EnrollStudentInCourseCommand(TenantId tenantId, CourseId courseId, StudentId studentId) {}
