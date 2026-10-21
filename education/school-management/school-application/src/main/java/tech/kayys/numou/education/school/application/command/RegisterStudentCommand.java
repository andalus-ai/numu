package tech.kayys.numou.education.school.application.command;

import tech.kayys.numou.education.core.Gender;
import tech.kayys.numou.education.core.Religion;
import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.LocalDate;

/** {@code nisn} may be null for newly admitted students; {@code nis} is the school's own number. */
public record RegisterStudentCommand(TenantId tenantId, SchoolId schoolId, String fullName, Gender gender,
                                     LocalDate birthDate, String birthPlace, Religion religion,
                                     String nisn, String nis) {}
