package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.pesantren.domain.LeavePermitId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.LocalDate;

public record ReturnFromLeaveCommand(TenantId tenantId, LeavePermitId permitId, LocalDate returnedOn) {}
