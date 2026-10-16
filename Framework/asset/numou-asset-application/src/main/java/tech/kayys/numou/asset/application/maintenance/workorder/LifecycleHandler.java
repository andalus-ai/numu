package tech.kayys.numou.asset.application.maintenance.workorder;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.repository.MaintenanceWorkOrderRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.util.function.BiConsumer;

public abstract class LifecycleHandler<C> extends AbstractMaintenanceCommandHandler {

    protected LifecycleHandler(MaintenanceWorkOrderRepository repository, EventPublisher eventPublisher,
                               UnitOfWork unitOfWork, DomainClock clock) {
        super(repository, eventPublisher, unitOfWork, clock);
    }

    protected Uni<Result<MaintenanceWorkOrderResult>> transition(String tenantId, java.util.UUID workOrderId,
                                                                 BiConsumer<tech.kayys.numou.asset.domain.maintenance.workorder.MaintenanceWorkOrder, DomainClock> op) {
        return requireWorkOrder(tenantId, workOrderId)
                .flatMap(wo -> {
                    op.accept(wo, clock);
                    return save(tenantId, wo);
                })
                .map(saved -> Result.success(MaintenanceWorkOrderResult.from(saved)));
    }
}
