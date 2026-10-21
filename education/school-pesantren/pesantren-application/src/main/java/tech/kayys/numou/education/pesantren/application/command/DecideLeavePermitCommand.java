package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.pesantren.domain.LeavePermitId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record DecideLeavePermitCommand(TenantId tenantId, LeavePermitId permitId, boolean approve, String decidedBy, String note) {}
