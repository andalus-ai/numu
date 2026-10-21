package tech.kayys.numou.education.school.application.command;

import tech.kayys.numou.education.core.Curriculum;
import tech.kayys.numou.education.core.EducationLevel;
import tech.kayys.numou.education.school.domain.SchoolOwnership;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record RegisterSchoolCommand(TenantId tenantId, String npsn, String name, EducationLevel level,
                                    Curriculum curriculum, SchoolOwnership ownership) {}
