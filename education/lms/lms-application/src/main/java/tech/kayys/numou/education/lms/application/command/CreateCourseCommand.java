package tech.kayys.numou.education.lms.application.command;

import tech.kayys.numou.education.core.AcademicYear;
import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.education.core.Semester;
import tech.kayys.numou.education.core.TeacherId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record CreateCourseCommand(TenantId tenantId, SchoolId schoolId, TeacherId instructor, String title, String code,
                                  AcademicYear academicYear, Semester semester) {}
