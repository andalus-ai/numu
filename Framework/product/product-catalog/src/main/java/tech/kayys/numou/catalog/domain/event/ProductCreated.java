package tech.kayys.syirkah.catalog.domain.event;

import tech.kayys.syirkah.catalog.domain.model.CatalogProduct;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Catalog (bounded-context) domain event: a Catalog product was created.
 *
 * <p>This is <b>not</b> the Product foundation
 * {@code ProductCreated} event. It carries catalog-listing fields
 * (price, currency) that the Product foundation deliberately does not
 * expose (product00.md).</p>
 */
public class ProductCreated implements DomainEvent {

    private static final long serialVersionUID = 1L;

    private final UUID eventId;
    private final String eventType;
    private final Instant occurredAt;
    private final String aggregateId;
    private final String aggregateType;
    private final String productName;
    private final String sku;
    private final String price;
    private final String currency;

    public ProductCreated(CatalogProduct product) {
        this.eventId = UUID.randomUUID();
        this.eventType = "ProductCreated";
        this.occurredAt = Instant.now();
        this.aggregateId = product.getId().toString();
        this.aggregateType = "CatalogProduct";
        this.productName = product.getName();
        this.sku = product.getSku();
        this.price = product.getPrice().getAmount().toPlainString();
        this.currency = product.getPrice().getCurrency().getCurrencyCode();
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

    public String getProductName() {
        return productName;
    }

    public String getSku() {
        return sku;
    }

    public String getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    @Override
    public String toString() {
        return "ProductCreated{" +
                "eventId=" + eventId +
                ", productName='" + productName + '\'' +
                ", sku='" + sku + '\'' +
                '}';
    }
}
