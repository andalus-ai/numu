package tech.kayys.numou.workforce.domain.learning;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.learning.event.LearningProgramActivated;
import tech.kayys.numou.workforce.domain.learning.event.LearningProgramCreated;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class LearningProgram extends AbstractAggregateRoot<LearningProgramId> {

    private final TenantId tenantId;
    private final String code;
    private String name;
    private String description;
    private final LearningProgramType type;
    private LearningProgramStatus status;
    private Duration estimatedDuration;

    private LearningProgram(
            LearningProgramId id,
            TenantId tenantId,
            String code,
            String name,
            String description,
            LearningProgramType type,
            Duration estimatedDuration
    ) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = description;
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.estimatedDuration = estimatedDuration;
        this.status = LearningProgramStatus.DRAFT;
    }

    public static LearningProgram create(
            LearningProgramId id,
            TenantId tenantId,
            String code,
            String name,
            String description,
            LearningProgramType type,
            Duration estimatedDuration
    ) {
        LearningProgram program = new LearningProgram(id, tenantId, code, name, description, type, estimatedDuration);
        program.raise(new LearningProgramCreated(id, tenantId, code, name));
        return program;
    }

    public void activate() {
        if (status == LearningProgramStatus.ACTIVE) {
            return;
        }
        this.status = LearningProgramStatus.ACTIVE;
        incrementVersion();
        this.updatedAt = Instant.now();
        raise(new LearningProgramActivated(getId()));
    }

    public void deactivate() {
        this.status = LearningProgramStatus.INACTIVE;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public LearningProgramType getType() { return type; }
    public LearningProgramStatus getStatus() { return status; }
    public Duration getEstimatedDuration() { return estimatedDuration; }
}
