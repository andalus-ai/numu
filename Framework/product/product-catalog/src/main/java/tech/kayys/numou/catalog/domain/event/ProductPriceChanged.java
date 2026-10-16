package tech.kayys.syirkah.catalog.domain.event;

import tech.kayys.syirkah.catalog.domain.model.CatalogProduct;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Catalog (bounded-context) domain event: a Catalog product's price
 * changed.
 *
 * <p>This is <b>not</b> the Product foundation event. It carries
 * catalog-listing fields (price, currency) that the Product foundation
 * deliberately does not expose (product00.md).</p>
 */
public class ProductPriceChanged implements DomainEvent {

    private static final long serialVersionUID = 1L;

    private final UUID eventId;
    private final String eventType;
    private final Instant occurredAt;
    private final String aggregateId;
    private final String aggregateType;
    private final String oldPrice;
    private final String newPrice;
    private final String currency;

    public ProductPriceChanged(CatalogProduct product, Money oldPrice, Money newPrice) {
        this.eventId = UUID.randomUUID();
        this.eventType = "ProductPriceChanged";
        this.occurredAt = Instant.now();
        this.aggregateId = product.getId().toString();
        this.aggregateType = "CatalogProduct";
        this.oldPrice = oldPrice.getAmount().toPlainString();
        this.newPrice = newPrice.getAmount().toPlainString();
        this.currency = newPrice.getCurrency().getCurrencyCode();
    }

    @Override
    public UUID eventId() {
        return eventId;
    }

    @Override
    public String eventType() {
        return eventType;
    }

    @Override
    public Instant occurredAt() {
        return occurredAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getOldPrice() {
        return oldPrice;
    }

    public String getNewPrice() {
        return newPrice;
    }

    public String getCurrency() {
        return currency;
    }

    @Override
    public String toString() {
        return "ProductPriceChanged{" +
                "eventId=" + eventId +
                ", oldPrice=" + oldPrice +
                ", newPrice=" + newPrice +
                '}';
    }
}
