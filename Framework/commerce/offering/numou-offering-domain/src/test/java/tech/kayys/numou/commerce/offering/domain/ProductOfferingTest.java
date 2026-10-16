package tech.kayys.syirkah.commerce.offering.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.offering.domain.event.ProductOfferingActivated;
import tech.kayys.syirkah.commerce.offering.domain.event.ProductOfferingCreated;
import tech.kayys.syirkah.commerce.offering.domain.event.ProductOfferingRetired;
import tech.kayys.syirkah.commerce.offering.domain.event.ProductOfferingSuspended;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProductOffering domain invariants and lifecycle")
class ProductOfferingTest {

    private final ProductId productId = ProductId.generate();
    private final ChannelId channelId = ChannelId.of("ONLINE_STORE");
    private final DateRange validity = new DateRange(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31)
    );

    private ProductOffering createOffering() {
        return ProductOffering.create(
                ProductOfferingId.generate(),
                productId,
                "Standard Coffee Offering",
                OfferingType.STANDARD,
                channelId,
                UUID.randomUUID(),
                validity
        );
    }

    @Test
    void createsOfferingInDraftAndRaisesEvent() {
        var offering = createOffering();

        assertEquals(OfferingStatus.DRAFT, offering.status());
        assertEquals("Standard Coffee Offering", offering.name());
        assertEquals(productId, offering.productId());
        assertEquals(channelId, offering.channelId());

        var events = offering.pullDomainEvents();
        assertEquals(1, events.size());
        assertInstanceOf(ProductOfferingCreated.class, events.getFirst());
    }

    @Test
    void transitionsThroughLifecycle() {
        var offering = createOffering();
        offering.pullDomainEvents();

        offering.activate();
        assertEquals(OfferingStatus.ACTIVE, offering.status());
        assertInstanceOf(ProductOfferingActivated.class, offering.pullDomainEvents().getFirst());

        offering.suspend();
        assertEquals(OfferingStatus.SUSPENDED, offering.status());
        assertInstanceOf(ProductOfferingSuspended.class, offering.pullDomainEvents().getFirst());

        offering.activate();
        assertEquals(OfferingStatus.ACTIVE, offering.status());
        offering.pullDomainEvents();

        offering.retire();
        assertEquals(OfferingStatus.RETIRED, offering.status());
        assertInstanceOf(ProductOfferingRetired.class, offering.pullDomainEvents().getFirst());

        assertThrows(InvalidStateException.class, offering::activate);
    }

    @Test
    void cannotSuspendDraftOffering() {
        var offering = createOffering();
        assertThrows(InvalidStateException.class, offering::suspend);
    }
}
