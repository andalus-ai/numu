package tech.kayys.syirkah.integration.spi.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.event.domain.BusinessEvent;
import tech.kayys.syirkah.event.domain.EventEnvelope;

/**
 * Delivers a signed webhook to a partner endpoint.
 *
 * <p>Implementations own retries, backoff and signing; callers own only
 * "this event must reach that subscription".
 */
public interface OutboundWebhookPort {

    /**
     * @param deliveryId idempotency key of this delivery attempt
     * @param targetUrl  the subscription's https endpoint
     * @param secretHandle resolved handle of the signing secret
     */
    Uni<Void> deliver(
            String deliveryId,
            String targetUrl,
            String secretHandle,
            EventEnvelope<? extends BusinessEvent> envelope);
}
