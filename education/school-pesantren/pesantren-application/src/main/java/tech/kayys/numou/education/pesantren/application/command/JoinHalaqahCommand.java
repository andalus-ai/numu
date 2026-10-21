package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.pesantren.domain.HalaqahId;
import tech.kayys.numou.education.pesantren.domain.SantriId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record JoinHalaqahCommand(TenantId tenantId, HalaqahId halaqahId, SantriId santriId) {}
