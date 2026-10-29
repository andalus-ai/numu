package tech.kayys.numou.pos.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.pos.application.port.in.PosEventInbox;
import tech.kayys.numou.pos.application.port.out.PosOutboxEvent;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PosEventConsumerTest {
    @Test
    void runsHandlerOnlyOnceForSameConsumerAndEvent() {
        Set<String> processed = new HashSet<>();
        PosEventInbox inbox = (tenant, consumer, event, handler) -> {
            String key = tenant + ":" + consumer + ":" + event;
            if (!processed.add(key)) return false;
            handler.run();
            return true;
        };
        PosEventConsumer service = new PosEventConsumer(inbox);
        PosOutboxEvent event = event();
        AtomicInteger effects = new AtomicInteger();

        assertTrue(service.consume("inventory-projection", event, effects::incrementAndGet));
        assertFalse(service.consume("inventory-projection", event, effects::incrementAndGet));
        assertEquals(1, effects.get());
    }

    @Test
    void allowsIndependentConsumersToHandleSameEvent() {
        Set<String> processed = new HashSet<>();
        PosEventInbox inbox = (tenant, consumer, event, handler) -> {
            if (!processed.add(tenant + ":" + consumer + ":" + event)) return false;
            handler.run();
            return true;
        };
        PosEventConsumer service = new PosEventConsumer(inbox);
        PosOutboxEvent event = event();
        AtomicInteger effects = new AtomicInteger();

        assertTrue(service.consume("finance-ledger", event, effects::incrementAndGet));
        assertTrue(service.consume("inventory-projection", event, effects::incrementAndGet));
        assertEquals(2, effects.get());
    }

    @Test
    void handlerFailureIsPropagatedForRetry() {
        PosEventConsumer service = new PosEventConsumer((tenant, consumer, event, handler) -> handler.getClass() != null && failHandler(handler));
        assertThrows(IllegalStateException.class,
                () -> service.consume("finance-ledger", event(), () -> { throw new IllegalStateException("temporary failure"); }));
    }

    private static boolean failHandler(Runnable handler) {
        handler.run();
        return true;
    }

    private static PosOutboxEvent event() {
        return new PosOutboxEvent(UUID.randomUUID(), UUID.randomUUID(), "Sale",
                UUID.randomUUID().toString(), "pos.sale.completed", 1, Instant.parse("2026-01-01T00:00:00Z"), "{}");
    }
}
