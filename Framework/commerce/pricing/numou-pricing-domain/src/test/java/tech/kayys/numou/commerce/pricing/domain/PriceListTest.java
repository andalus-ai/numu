package tech.kayys.syirkah.commerce.pricing.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.commerce.pricing.domain.price.PriceList;
import tech.kayys.syirkah.commerce.pricing.domain.price.PriceListId;
import tech.kayys.syirkah.commerce.pricing.domain.price.PriceListStatus;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PriceListTest {

    private static ProductOfferingId offering() {
        return new ProductOfferingId(UUID.randomUUID());
    }

    @Test
    void lifecycleAndEntries() {
        var list = PriceList.create(PriceListId.generate(), "retail-2026", "Retail 2026");
        assertEquals(PriceListStatus.DRAFT, list.status());
        assertEquals("RETAIL-2026", list.code());

        var offering = offering();
        var entry = list.addEntry(offering, Money.of(50_000, "IDR"));
        assertEquals(entry, list.entryFor(offering).orElseThrow());

        list.changeEntry(offering, Money.of(45_000, "IDR"));
        assertEquals(0, list.entryFor(offering).orElseThrow().amount().amount()
                .compareTo(new java.math.BigDecimal("45000")));

        list.activate();
        assertEquals(PriceListStatus.ACTIVE, list.status());

        list.suspend();
        assertEquals(PriceListStatus.SUSPENDED, list.status());

        list.archive();
        assertEquals(PriceListStatus.ARCHIVED, list.status());
        assertFalse(list.pullDomainEvents().isEmpty());
    }

    @Test
    void duplicateEntryRejected() {
        var list = PriceList.create(PriceListId.generate(), "RETAIL", "Retail");
        list.addEntry(offering(), Money.of(10_000, "IDR"));
        var same = list.entries().getFirst().offeringId();
        assertThrows(BusinessRuleViolation.class,
                () -> list.addEntry(same, Money.of(12_000, "IDR")));
    }

    @Test
    void archivedListIsImmutable() {
        var list = PriceList.create(PriceListId.generate(), "RETAIL", "Retail");
        list.activate();
        list.suspend();
        list.archive();
        assertThrows(BusinessRuleViolation.class,
                () -> list.addEntry(offering(), Money.of(10_000, "IDR")));
    }
}
