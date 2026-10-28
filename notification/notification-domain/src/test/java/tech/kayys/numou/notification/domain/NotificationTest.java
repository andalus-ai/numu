package tech.kayys.numou.notification.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationTest {
    private final Instant created = Instant.parse("2026-10-28T00:00:00Z");

    @Test
    void createsUnreadNotificationWithNormalizedText() {
        Notification n = Notification.create(UUID.randomUUID(), UUID.randomUUID(),
                " invoice.overdue ", " Payment due ", "Your invoice is overdue",
                null, "invoice-123-overdue-v1", created);

        assertFalse(n.isRead());
        assertEquals("invoice.overdue", n.type());
        assertEquals("Payment due", n.title());
        assertEquals("", n.sourceReference());
    }

    @Test
    void readingIsIdempotent() {
        Notification n = Notification.create(UUID.randomUUID(), UUID.randomUUID(),
                "task.assigned", "Task assigned", "You have a new task",
                "task:42", "task-assigned-42", created);

        Notification read = n.markRead(created.plusSeconds(30));
        assertTrue(read.isRead());
        assertSame(read, read.markRead(created.plusSeconds(60)));
        assertEquals(created.plusSeconds(30), read.readAt());
    }

    @Test
    void rejectsReadTimeBeforeCreation() {
        Notification n = Notification.create(UUID.randomUUID(), UUID.randomUUID(),
                "task.assigned", "Task assigned", "You have a new task",
                "", "task-assigned-42", created);

        assertThrows(IllegalArgumentException.class, () -> n.markRead(created.minusSeconds(1)));
    }

    @Test
    void rejectsOversizedContent() {
        assertThrows(IllegalArgumentException.class, () -> Notification.create(
                UUID.randomUUID(), UUID.randomUUID(), "task.assigned", "Task assigned",
                "x".repeat(Notification.MAX_BODY_LENGTH + 1), "", "key-1", created));
    }

    @Test
    void requiresTenantRecipientAndIdempotencyKey() {
        assertThrows(NullPointerException.class, () -> Notification.create(
                null, UUID.randomUUID(), "task.assigned", "Task assigned",
                "You have a new task", "", "key-1", created));
        assertThrows(IllegalArgumentException.class, () -> Notification.create(
                UUID.randomUUID(), UUID.randomUUID(), "task.assigned", "Task assigned",
                "You have a new task", "", " ", created));
    }
}