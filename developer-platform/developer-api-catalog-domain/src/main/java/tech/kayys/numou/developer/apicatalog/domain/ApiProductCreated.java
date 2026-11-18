package tech.kayys.numou.developer.apicatalog.domain;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;
public record ApiProductCreated(UUID eventId, Instant occurredAt, ApiProductId productId, UUID providerTenantId, String slug) implements DomainEvent {
 @Override public String eventType(){return "developer.api-product.created";}
}