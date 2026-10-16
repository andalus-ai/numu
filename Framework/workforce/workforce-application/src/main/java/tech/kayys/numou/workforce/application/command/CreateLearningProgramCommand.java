package tech.kayys.numou.workforce.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.learning.LearningProgramType;

import java.time.Duration;
import java.util.Objects;

public record CreateLearningProgramCommand(
        TenantId tenantId,
        String code,
        String name,
        String description,
        LearningProgramType type,
        Duration estimatedDuration
) implements Command {
    public CreateLearningProgramCommand {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }
}
