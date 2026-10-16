package tech.kayys.syirkah.commerce.pricing.adapter.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("In-memory price book and option price store")
class InMemoryPricingStoresTest {

    @Test
    void storesBasePriceAndOptionAdjustments() {
        var offeringId = ProductOfferingId.generate();
        var priceBook = new InMemoryPriceBook();
        var optionPrices = new InMemoryOptionPriceStore();

        priceBook.put(offeringId, Money.of(new BigDecimal("30000"), "IDR"));
        optionPrices.put(
                offeringId, "MILK", "OAT", Money.of(new BigDecimal("7000"), "IDR"));

        assertTrue(priceBook.findBasePrice(offeringId)
                .toCompletableFuture().join().isPresent());
        assertTrue(optionPrices.findAdjustment(offeringId, "MILK", "OAT")
                .toCompletableFuture().join().isPresent());
        assertTrue(optionPrices.findAdjustment(offeringId, "MILK", "NONE")
                .toCompletableFuture().join().isEmpty());
    }
}
