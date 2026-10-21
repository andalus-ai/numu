package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.pesantren.domain.DormRoomId;
import tech.kayys.numou.education.pesantren.domain.DormitoryId;
import tech.kayys.numou.education.pesantren.domain.SantriId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record AssignSantriToRoomCommand(TenantId tenantId, DormitoryId dormitoryId, DormRoomId roomId, SantriId santriId) {}
