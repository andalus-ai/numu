package tech.kayys.numou.event.application.mapper;

import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

/**
 * Translates one bounded context's private domain event into the public
 * business event contract (base01.md §P1-12).
 *
 * <p>This is the seam that keeps internal refactoring cheap: a domain
 * event may be renamed or split freely as long as its published business
 * event contract is preserved.
 *
 * @param <S> the source domain event type
 * @param <T> the published business event type
 */
public interface DomainEventTranslator<S extends DomainEvent, T extends BusinessEvent> {

    boolean supports(DomainEvent event);

    T translate(S domainEvent);
}
