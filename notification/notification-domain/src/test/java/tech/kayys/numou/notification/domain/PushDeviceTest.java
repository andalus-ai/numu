package tech.kayys.numou.notification.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PushDeviceTest {
    private final Instant created = Instant.parse("2026-10-28T00:00:00Z");

    @Test
    void registersActiveDeviceAndNormalizesMetadata() {
        PushDevice device = PushDevice.register(UUID.randomUUID(), UUID.randomUUID(),
                " token-secret ", PushDevice.Platform.ANDROID, " app-1 ", created);

        assertTrue(device.isActive());
        assertEquals("token-secret", device.token());
        assertEquals("app-1", device.appInstance());
        assertFalse(device.toString().contains("token-secret"));
        assertTrue(device.toString().contains("[REDACTED]"));
    }

    @Test
    void disableIsIdempotentAndPreservesFirstTimestamp() {
        PushDevice device = PushDevice.register(UUID.randomUUID(), UUID.randomUUID(),
                "token", PushDevice.Platform.IOS, "", created);

        PushDevice disabled = device.disable(created.plusSeconds(10));
        assertFalse(disabled.isActive());
        assertSame(disabled, disabled.disable(created.plusSeconds(20)));
        assertEquals(created.plusSeconds(10), disabled.disabledAt());
    }

    @Test
    void refreshReactivatesAndDoesNotMoveTimeBackwards() {
        PushDevice device = PushDevice.register(UUID.randomUUID(), UUID.randomUUID(),
                "token", PushDevice.Platform.WEB, "", created).disable(created.plusSeconds(10));

        PushDevice refreshed = device.refresh("new-token", PushDevice.Platform.WEB,
                "browser-2", created.plusSeconds(20));
        assertTrue(refreshed.isActive());
        assertEquals("new-token", refreshed.token());
        assertEquals(created, refreshed.createdAt());
    }

    @Test
    void rejectsInvalidPlatformMetadataAndTime() {
        assertThrows(NullPointerException.class, () -> PushDevice.register(
                UUID.randomUUID(), UUID.randomUUID(), "token", null, "", created));
        assertThrows(IllegalArgumentException.class, () -> PushDevice.register(
                UUID.randomUUID(), UUID.randomUUID(), " ", PushDevice.Platform.ANDROID, "", created));
        assertThrows(IllegalArgumentException.class, () -> PushDevice.register(
                UUID.randomUUID(), UUID.randomUUID(), "token", PushDevice.Platform.ANDROID,
                "x".repeat(PushDevice.MAX_APP_INSTANCE_LENGTH + 1), created));
        PushDevice device = PushDevice.register(UUID.randomUUID(), UUID.randomUUID(),
                "token", PushDevice.Platform.ANDROID, "", created);
        assertThrows(IllegalArgumentException.class, () -> device.disable(created.minusSeconds(1)));
    }
}
