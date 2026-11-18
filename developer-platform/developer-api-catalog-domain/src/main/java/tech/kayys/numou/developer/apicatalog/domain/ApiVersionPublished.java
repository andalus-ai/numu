package tech.kayys.numou.developer.apicatalog.domain;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;
public record ApiVersionPublished(UUID eventId, Instant occurredAt, ApiProductId productId, String version) implements DomainEvent {
 @Override public String eventType(){return "developer.api-version.published";}
}