package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.education.pesantren.domain.PesantrenType;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

/** {@code nspp} and {@code affiliatedSchool} are optional. */
public record RegisterPesantrenCommand(TenantId tenantId, String name, String nspp, PesantrenType type,
                                       SchoolId affiliatedSchool) {}
