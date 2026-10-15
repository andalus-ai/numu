package tech.kayys.syirkah.construction.application.boq.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.construction.application.boq.command.ApproveBoqRevisionCommand;
import tech.kayys.syirkah.construction.domain.boq.BoqRevisionSnapshot;
import tech.kayys.syirkah.construction.spi.boq.BoqRevisionWorkflow;
import tech.kayys.syirkah.construction.spi.boq.BoqRevisionActorContext;
import tech.kayys.syirkah.construction.spi.boq.BoqRevisionTenantContext;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;

import java.time.Clock;
import java.util.Objects;

public final class ApproveBoqRevisionHandler
        implements CommandHandler<ApproveBoqRevisionCommand, BoqRevisionSnapshot> {
    private final BoqRevisionWorkflow workflow;
    private final Clock clock;
    private final BoqRevisionActorContext actorContext;
    private final BoqRevisionTenantContext tenantContext;

    public ApproveBoqRevisionHandler(BoqRevisionWorkflow workflow, Clock clock, BoqRevisionActorContext actorContext,
                                     BoqRevisionTenantContext tenantContext) {
        this.workflow = Objects.requireNonNull(workflow, "Workflow cannot be null");
        this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
        this.actorContext = Objects.requireNonNull(actorContext, "Actor context cannot be null");
        this.tenantContext = Objects.requireNonNull(tenantContext, "Tenant context cannot be null");
    }

    @Override
    public Uni<BoqRevisionSnapshot> handle(ApproveBoqRevisionCommand command) {
        return Uni.createFrom().completionStage(workflow.approve(
                requireTenantId(), command.boqId(), command.revisionNumber(), actorContext.requireActorId(), clock.instant()));
    }
    private String requireTenantId() {
        String tenantId = tenantContext.requireTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Authenticated tenant context is required");
        }
        return tenantId;
    }
}
