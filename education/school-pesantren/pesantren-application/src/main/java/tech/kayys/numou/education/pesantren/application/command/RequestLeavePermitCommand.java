package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.pesantren.domain.SantriId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.LocalDate;

public record RequestLeavePermitCommand(TenantId tenantId, SantriId santriId, LocalDate from, LocalDate to, String reason) {}
