package tech.kayys.numou.event.application.mapper;

import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Registry of translators, so one module can publish several event contracts
 * without the dispatcher knowing any of them.
 */
public final class DomainEventTranslatorRegistry {

    private final List<DomainEventTranslator<?, ?>> translators = new ArrayList<>();

    public DomainEventTranslatorRegistry register(DomainEventTranslator<?, ?> translator) {
        translators.add(Objects.requireNonNull(translator, "translator cannot be null"));
        return this;
    }

    /**
     * Translates a domain event, or returns empty when nothing in this
     * registry claims it (an event may legitimately stay private).
     */
    @SuppressWarnings("unchecked")
    public Optional<BusinessEvent> translate(DomainEvent event) {
        return translators.stream()
                .filter(translator -> translator.supports(event))
                .findFirst()
                .map(translator -> ((DomainEventTranslator<DomainEvent, BusinessEvent>) translator)
                        .translate(event));
    }

    public int size() {
        return translators.size();
    }
}
