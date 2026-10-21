package tech.kayys.numou.education.school.application.command;

import tech.kayys.numou.education.school.domain.ReportCardId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record FinalizeReportCardCommand(TenantId tenantId, ReportCardId reportCardId) {}
