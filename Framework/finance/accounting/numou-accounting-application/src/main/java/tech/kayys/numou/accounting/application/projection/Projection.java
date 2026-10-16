package tech.kayys.numou.accounting.application.projection;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.accounting.domain.event.AccountingEvent;

/**
 * Contract for event-driven read model updaters.
 */
public interface Projection<E extends AccountingEvent> {
    Uni<Void> project(E event);
}
