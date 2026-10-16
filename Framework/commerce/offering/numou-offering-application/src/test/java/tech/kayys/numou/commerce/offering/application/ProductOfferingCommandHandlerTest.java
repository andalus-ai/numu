package tech.kayys.syirkah.commerce.offering.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.offering.application.command.ActivateProductOfferingCommand;
import tech.kayys.syirkah.commerce.offering.application.command.CreateProductOfferingCommand;
import tech.kayys.syirkah.commerce.offering.application.command.RetireProductOfferingCommand;
import tech.kayys.syirkah.commerce.offering.application.command.SuspendProductOfferingCommand;
import tech.kayys.syirkah.commerce.offering.application.handler.ActivateProductOfferingHandler;
import tech.kayys.syirkah.commerce.offering.application.handler.CreateProductOfferingHandler;
import tech.kayys.syirkah.commerce.offering.application.handler.RetireProductOfferingHandler;
import tech.kayys.syirkah.commerce.offering.application.handler.SuspendProductOfferingHandler;
import tech.kayys.syirkah.commerce.offering.application.support.InMemoryProductOfferingRepository;
import tech.kayys.syirkah.commerce.offering.application.support.RecordingEventPublisher;
import tech.kayys.syirkah.commerce.offering.domain.ChannelId;
import tech.kayys.syirkah.commerce.offering.domain.OfferingType;
import tech.kayys.syirkah.commerce.offering.domain.event.ProductOfferingActivated;
import tech.kayys.syirkah.commerce.offering.domain.event.ProductOfferingCreated;
import tech.kayys.syirkah.commerce.offering.domain.event.ProductOfferingRetired;
import tech.kayys.syirkah.commerce.offering.domain.event.ProductOfferingSuspended;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Product offering command handlers")
class ProductOfferingCommandHandlerTest {

    private final InMemoryProductOfferingRepository repository = new InMemoryProductOfferingRepository();
    private final RecordingEventPublisher events = new RecordingEventPublisher();

    private final CreateProductOfferingHandler createHandler = new CreateProductOfferingHandler(repository, events);
    private final ActivateProductOfferingHandler activateHandler = new ActivateProductOfferingHandler(repository, events);
    private final SuspendProductOfferingHandler suspendHandler = new SuspendProductOfferingHandler(repository, events);
    private final RetireProductOfferingHandler retireHandler = new RetireProductOfferingHandler(repository, events);

    @Test
    void handlesFullOfferingLifecycle() {
        var createCmd = new CreateProductOfferingCommand(
                ProductId.generate(),
                "Summer Promotion",
                OfferingType.PROMOTIONAL,
                ChannelId.of("WEB_PORTAL"),
                UUID.randomUUID(),
                new DateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31))
        );

        var id = createHandler.handle(createCmd).await().indefinitely().orElseThrow();
        assertNotNull(id);
        assertInstanceOf(ProductOfferingCreated.class, events.published().getLast());

        var activateResult = activateHandler.handle(new ActivateProductOfferingCommand(id)).await().indefinitely();
        assertTrue(activateResult.isSuccess());
        assertInstanceOf(ProductOfferingActivated.class, events.published().getLast());

        var suspendResult = suspendHandler.handle(new SuspendProductOfferingCommand(id)).await().indefinitely();
        assertTrue(suspendResult.isSuccess());
        assertInstanceOf(ProductOfferingSuspended.class, events.published().getLast());

        var retireResult = retireHandler.handle(new RetireProductOfferingCommand(id)).await().indefinitely();
        assertTrue(retireResult.isSuccess());
        assertInstanceOf(ProductOfferingRetired.class, events.published().getLast());
    }
}
