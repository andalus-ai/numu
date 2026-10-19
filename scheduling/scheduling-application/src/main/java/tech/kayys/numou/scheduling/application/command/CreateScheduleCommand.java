package tech.kayys.numou.scheduling.application.command;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.LocalDate;

public record CreateScheduleCommand(
        TenantId tenantId,
        String name,
        LocalDate startDate,
        LocalDate endDate
) {}
