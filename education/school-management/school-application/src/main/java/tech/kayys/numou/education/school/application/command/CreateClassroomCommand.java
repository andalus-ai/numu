package tech.kayys.numou.education.school.application.command;

import tech.kayys.numou.education.core.AcademicYear;
import tech.kayys.numou.education.core.GradeLevel;
import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.education.core.TeacherId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

/** {@code homeroomTeacher} is optional. */
public record CreateClassroomCommand(TenantId tenantId, SchoolId schoolId, String name, GradeLevel gradeLevel,
                                     AcademicYear academicYear, int capacity, TeacherId homeroomTeacher) {}
