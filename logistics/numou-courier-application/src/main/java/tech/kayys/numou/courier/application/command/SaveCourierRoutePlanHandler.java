package tech.kayys.numou.courier.application.command;

import tech.kayys.numou.courier.application.port.CourierPlanningAccess;
import tech.kayys.numou.courier.domain.CourierPlanningRepository;
import tech.kayys.numou.courier.domain.CourierRoutePlan;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Enforces branch-level access before persisting a complete route-plan snapshot. */
public final class SaveCourierRoutePlanHandler {
    private final CourierPlanningRepository planning;
    private final CourierPlanningAccess access;

    public SaveCourierRoutePlanHandler(CourierPlanningRepository planning, CourierPlanningAccess access) {
        this.planning=Objects.requireNonNull(planning); this.access=Objects.requireNonNull(access);
    }

    public CompletionStage<CourierRoutePlan> handle(SaveCourierRoutePlanCommand command) {
        Objects.requireNonNull(command);
        return access.assertCanManageRoutePlan(command.tenantId(), command.routePlan().branchCode(), command.actorReference())
            .thenCompose(ignored -> planning.saveRoutePlan(command.tenantId(), command.routePlan()));
    }
}
