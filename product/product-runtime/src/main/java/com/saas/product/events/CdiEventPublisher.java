package com.saas.product.events;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Default CDI-based in-process event publisher.
 *
 * Observers subscribe using:
 *   void onProductCreated(@Observes ProductEvent.ProductCreated evt) { ... }
 *
 * For transactional observers (fire after commit):
 *   @Observes(during = TransactionPhase.AFTER_SUCCESS)
 *
 * To switch to Kafka, replace this bean with @Alternative KafkaEventPublisher
 * and set quarkus.arc.selected-alternatives in application.properties.
 */
@ApplicationScoped
public class CdiEventPublisher implements EventPublisher {

    private static final Logger LOG = Logger.getLogger(CdiEventPublisher.class);

    @Inject
    Event<ProductEvent> cdiEvent;

    @Override
    public void publish(ProductEvent event) {
        try {
            cdiEvent.fire(event);
        } catch (Exception e) {
            // Never let event publishing break the primary transaction
            LOG.errorf(e, "Failed to publish event %s for product %s",
                    event.getClass().getSimpleName(), event.productId());
        }
    }
}
