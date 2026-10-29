package tech.kayys.numou.queuemanagement.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueueRequestFingerprintTest {

    @Test
    void sameNormalizedComponentsProduceSameFingerprint() {
        assertEquals(
                QueueRequestFingerprint.sha256("queue.entry.join", "queue-1", "orders", "order", "42", "10"),
                QueueRequestFingerprint.sha256("queue.entry.join", "queue-1", "orders", "order", "42", "10"));
    }

    @Test
    void componentBoundariesAreUnambiguous() {
        assertNotEquals(
                QueueRequestFingerprint.sha256("ab", "c"),
                QueueRequestFingerprint.sha256("a", "bc"));
    }

    @Test
    void changingAnyBusinessArgumentChangesFingerprint() {
        String original = QueueRequestFingerprint.sha256("queue.entry.join", "queue-1", "orders", "order", "42", "10");
        assertNotEquals(original,
                QueueRequestFingerprint.sha256("queue.entry.join", "queue-1", "orders", "order", "42", "11"));
    }

    @Test
    void rejectsNullComponents() {
        assertThrows(IllegalArgumentException.class,
                () -> QueueRequestFingerprint.sha256("queue.entry.join", null));
    }
}
