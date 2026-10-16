package tech.kayys.numou.event.spi.port;

import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;

/**
 * Converts envelopes to and from their wire form.
 *
 * <p>Kept behind a port so the platform can migrate serialization formats
 * without touching a single business module (base01.md §18 "infrastructure
 * implementations remain replaceable").
 */
public interface EventSerializerPort {

    String serialize(EventEnvelope<? extends BusinessEvent> envelope);

    EventEnvelope<? extends BusinessEvent> deserialize(String payload, String eventType);
}
