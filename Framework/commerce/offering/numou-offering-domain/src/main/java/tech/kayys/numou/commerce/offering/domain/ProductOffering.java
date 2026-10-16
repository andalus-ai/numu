package tech.kayys.syirkah.commerce.offering.domain;

import tech.kayys.syirkah.commerce.offering.domain.event.ProductOfferingActivated;
import tech.kayys.syirkah.commerce.offering.domain.event.ProductOfferingCreated;
import tech.kayys.syirkah.commerce.offering.domain.event.ProductOfferingRetired;
import tech.kayys.syirkah.commerce.offering.domain.event.ProductOfferingSuspended;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * ProductOffering aggregate root (Commerce Phase C).
 * Represents how a core Product is packaged and made available
 * commercially in channels for specific periods.
 */
public final class ProductOffering extends AbstractAggregateRoot<ProductOfferingId> {

    private final ProductId productId;
    private final String name;
    private final OfferingType type;
    private final ChannelId channelId;
    private final UUID sellerId;

    private OfferingStatus status;
    private DateRange validity;

    private ProductOffering(
            ProductOfferingId id,
            ProductId productId,
            String name,
            OfferingType type,
            ChannelId channelId,
            UUID sellerId,
            DateRange validity
    ) {
        super(id);
        this.productId = Objects.requireNonNull(productId, "productId cannot be null");
        this.name = requireText(name, "name");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.channelId = Objects.requireNonNull(channelId, "channelId cannot be null");
        this.sellerId = sellerId;
        this.validity = validity;
        this.status = OfferingStatus.DRAFT;
    }

    public static ProductOffering create(
            ProductOfferingId id,
            ProductId productId,
            String name,
            OfferingType type,
            ChannelId channelId,
            UUID sellerId,
            DateRange validity
    ) {
        var offering = new ProductOffering(id, productId, name, type, channelId, sellerId, validity);
        offering.raise(new ProductOfferingCreated(
                UUID.randomUUID(),
                Instant.now(),
                id,
                productId,
                type
        ));
        return offering;
    }

    public void activate() {
        if (status == OfferingStatus.RETIRED) {
            throw new InvalidStateException("Retired offering cannot be activated");
        }
        if (status == OfferingStatus.ACTIVE) {
            return;
        }

        status = OfferingStatus.ACTIVE;
        raise(new ProductOfferingActivated(
                UUID.randomUUID(),
                Instant.now(),
                id(),
                productId
        ));
    }

    public void suspend() {
        if (status != OfferingStatus.ACTIVE) {
            throw new InvalidStateException("Only active offering can be suspended");
        }

        status = OfferingStatus.SUSPENDED;
        raise(new ProductOfferingSuspended(
                UUID.randomUUID(),
                Instant.now(),
                id(),
                productId
        ));
    }

    public void retire() {
        if (status == OfferingStatus.RETIRED) {
            return;
        }

        status = OfferingStatus.RETIRED;
        raise(new ProductOfferingRetired(
                UUID.randomUUID(),
                Instant.now(),
                id(),
                productId
        ));
    }

    public void updateValidity(DateRange newValidity) {
        if (status == OfferingStatus.RETIRED) {
            throw new InvalidStateException("Cannot update validity of retired offering");
        }
        this.validity = newValidity;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }

    public ProductId productId() {
        return productId;
    }

    public String name() {
        return name;
    }

    public OfferingType type() {
        return type;
    }

    public ChannelId channelId() {
        return channelId;
    }

    public UUID sellerId() {
        return sellerId;
    }

    public OfferingStatus status() {
        return status;
    }

    public DateRange validity() {
        return validity;
    }
}
