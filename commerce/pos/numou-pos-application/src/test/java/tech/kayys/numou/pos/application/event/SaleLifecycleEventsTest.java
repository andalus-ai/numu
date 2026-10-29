package tech.kayys.numou.pos.application.event;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.pos.application.port.out.PosOutboxEvent;
import tech.kayys.numou.pos.domain.Sale;
import tech.kayys.numou.pos.domain.SaleChannel;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaleLifecycleEventsTest {
    private static final Instant OPENED_AT = Instant.parse("2026-10-29T08:00:00Z");

    @Test
    void openedEventHasVersionAndTenantMetadata() {
        UUID tenantId = UUID.randomUUID();
        UUID saleId = UUID.randomUUID();
        Sale sale = Sale.open(saleId, tenantId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), SaleChannel.POS, "IDR", OPENED_AT);

        PosOutboxEvent event = SaleLifecycleEvents.opened(sale);

        assertEquals(tenantId, event.tenantId());
        assertEquals("Sale", event.aggregateType());
        assertEquals(saleId.toString(), event.aggregateId());
        assertEquals("pos.sale.opened", event.eventType());
        assertEquals(1, event.schemaVersion());
        assertEquals(OPENED_AT, event.occurredAt());
        assertTrue(event.payloadJson().contains("\"schemaVersion\":1"));
        assertTrue(event.payloadJson().contains("\"saleId\":\"" + saleId + "\""));
    }

    @Test
    void voidedEventUsesExplicitOccurrenceTime() {
        Sale sale = Sale.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), SaleChannel.POS, "IDR", OPENED_AT).voidSale();
        Instant voidedAt = OPENED_AT.plusSeconds(30);

        PosOutboxEvent event = SaleLifecycleEvents.voided(sale, voidedAt);

        assertEquals("pos.sale.voided", event.eventType());
        assertEquals(voidedAt, event.occurredAt());
        assertTrue(event.payloadJson().contains("\"voidedAt\":\"" + voidedAt + "\""));
    }

    @Test
    void completedEventRejectsNonCompletedSale() {
        Sale sale = Sale.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), SaleChannel.POS, "IDR", OPENED_AT);

        assertThrows(IllegalArgumentException.class, () -> SaleLifecycleEvents.completed(sale));
    }
}
