package tech.kayys.syirkah.integration.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.integration.domain.identifier.ExternalSystemId;
import tech.kayys.syirkah.integration.domain.identifier.WebhookSubscriptionId;
import tech.kayys.syirkah.integration.domain.model.WebhookSubscription;

import java.util.List;
import java.util.Optional;

/** Outbound port for webhook subscriptions. */
public interface WebhookSubscriptionRepository {

    Uni<WebhookSubscription> save(WebhookSubscription subscription);

    Uni<Optional<WebhookSubscription>> findById(WebhookSubscriptionId id);

    Uni<List<WebhookSubscription>> findActiveBySystem(ExternalSystemId externalSystemId);

    Uni<List<WebhookSubscription>> findActiveByEventType(String eventType);
}
