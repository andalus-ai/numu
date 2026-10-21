package tech.kayys.numou.education.madrasah.application.command;

import tech.kayys.numou.education.core.EducationLevel;
import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.education.madrasah.domain.MadrasahOwnership;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record RegisterMadrasahProfileCommand(TenantId tenantId, SchoolId schoolId, String nsm, EducationLevel level,
                                             MadrasahOwnership ownership, boolean tahfidzProgram) {}
