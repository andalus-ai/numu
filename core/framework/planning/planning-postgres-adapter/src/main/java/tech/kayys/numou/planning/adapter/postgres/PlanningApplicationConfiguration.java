package tech.kayys.numou.planning.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import tech.kayys.numou.planning.application.RegisterSchedulePlanScope;
import tech.kayys.numou.planning.application.port.SchedulePlanScopeStore;

/**
 * Composes framework-neutral planning use cases with their runtime ports.
 *
 * <p>The application module intentionally has no CDI dependency; bean lifecycle and
 * dependency injection belong to the runtime adapter.</p>
 */
@ApplicationScoped
public class PlanningApplicationConfiguration {

    @Produces
    @ApplicationScoped
    public RegisterSchedulePlanScope registerSchedulePlanScope(SchedulePlanScopeStore store) {
        return new RegisterSchedulePlanScope(store);
    }
}
