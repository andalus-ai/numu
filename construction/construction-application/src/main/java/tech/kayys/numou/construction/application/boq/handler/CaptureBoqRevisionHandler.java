package tech.kayys.numou.construction.application.boq.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.construction.application.boq.command.CaptureBoqRevisionCommand;
import tech.kayys.numou.construction.domain.boq.BoqRevisionSnapshot;
import tech.kayys.numou.construction.spi.boq.BoqRevisionWorkflow;
import tech.kayys.numou.construction.spi.boq.BoqRevisionActorContext;
import tech.kayys.numou.construction.spi.boq.BoqRevisionTenantContext;
import tech.kayys.numou.foundation.application.command.CommandHandler;

import java.time.Clock;
import java.util.Objects;

public final class CaptureBoqRevisionHandler
        implements CommandHandler<CaptureBoqRevisionCommand, BoqRevisionSnapshot> {
    private final BoqRevisionWorkflow workflow;
    private final Clock clock;
    private final BoqRevisionActorContext actorContext;
    private final BoqRevisionTenantContext tenantContext;

    public CaptureBoqRevisionHandler(BoqRevisionWorkflow workflow, Clock clock, BoqRevisionActorContext actorContext,
                                     BoqRevisionTenantContext tenantContext) {
        this.workflow = Objects.requireNonNull(workflow, "Workflow cannot be null");
        this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
        this.actorContext = Objects.requireNonNull(actorContext, "Actor context cannot be null");
        this.tenantContext = Objects.requireNonNull(tenantContext, "Tenant context cannot be null");
    }

    @Override
    public Uni<BoqRevisionSnapshot> handle(CaptureBoqRevisionCommand command) {
        return Uni.createFrom().completionStage(workflow.captureDraft(
                requireTenantId(), command.boqId(), actorContext.requireActorId(), command.calculationPolicy(), clock.instant()));
    }
    private String requireTenantId() {
        String tenantId = tenantContext.requireTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Authenticated tenant context is required");
        }
        return tenantId;
    }
}
