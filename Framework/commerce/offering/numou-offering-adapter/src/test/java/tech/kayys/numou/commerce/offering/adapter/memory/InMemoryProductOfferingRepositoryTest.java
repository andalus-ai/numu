package tech.kayys.syirkah.commerce.offering.adapter.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.offering.domain.ChannelId;
import tech.kayys.syirkah.commerce.offering.domain.OfferingType;
import tech.kayys.syirkah.commerce.offering.domain.ProductOffering;
import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("In-memory product offering repository")
class InMemoryProductOfferingRepositoryTest {

    private final InMemoryProductOfferingRepository repository = new InMemoryProductOfferingRepository();

    @Test
    void savesAndFindsOffering() {
        var productId = ProductId.generate();
        var channelId = ChannelId.of("MOBILE_APP");
        var offering = ProductOffering.create(
                ProductOfferingId.generate(),
                productId,
                "Mobile Combo",
                OfferingType.BUNDLE,
                channelId,
                UUID.randomUUID(),
                new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30))
        );

        repository.save(offering).toCompletableFuture().join();

        assertTrue(repository.existsById(offering.id()).toCompletableFuture().join());

        var byProduct = repository.findByProductId(productId).toCompletableFuture().join();
        assertEquals(1, byProduct.size());
        assertEquals("Mobile Combo", byProduct.getFirst().name());

        var byChannel = repository.findByChannelId(channelId).toCompletableFuture().join();
        assertEquals(1, byChannel.size());

        repository.delete(offering).toCompletableFuture().join();
        assertFalse(repository.existsById(offering.id()).toCompletableFuture().join());
    }
}
