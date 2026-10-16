package tech.kayys.syirkah.accounting.application.projection;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.event.AccountingEvent;

/**
 * Contract for event-driven read model updaters.
 */
public interface Projection<E extends AccountingEvent> {
    Uni<Void> project(E event);
}
