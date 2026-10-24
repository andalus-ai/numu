package tech.kayys.numou.courier.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DispatchWaveTest {
    private static final Instant T0 = Instant.parse("2026-10-24T00:00:00Z");

    @Test
    void departureRequiresCompleteCleanLoadReconciliation() {
        DispatchWave wave = wave();
        wave.expectParcel("P-1", T0.plusSeconds(1));
        wave.expectParcel("P-2", T0.plusSeconds(2));
        wave.beginLoading(T0.plusSeconds(3));
        assertEquals(DispatchWave.ScanOutcome.ACCEPTED, wave.scanParcel("P-1", T0.plusSeconds(4)));
        assertThrows(IllegalStateException.class, () -> wave.markReady(T0.plusSeconds(5)));
        assertEquals(1, wave.missingParcels().size());

        wave.scanParcel("P-2", T0.plusSeconds(6));
        assertEquals(DispatchWaveStatus.READY, ready(wave, T0.plusSeconds(7)).status());
        wave.depart(T0.plusSeconds(8));
        assertEquals(DispatchWaveStatus.DEPARTED, wave.status());
    }

    @Test
    void unexpectedAndDuplicateScansRemainVisibleAndBlockReady() {
        DispatchWave wave = wave();
        wave.expectParcel("P-1", T0.plusSeconds(1));
        wave.beginLoading(T0.plusSeconds(2));

        assertEquals(DispatchWave.ScanOutcome.UNEXPECTED, wave.scanParcel("P-X", T0.plusSeconds(3)));
        assertEquals(DispatchWave.ScanOutcome.ACCEPTED, wave.scanParcel("P-1", T0.plusSeconds(4)));
        assertEquals(DispatchWave.ScanOutcome.DUPLICATE, wave.scanParcel("P-1", T0.plusSeconds(5)));
        assertThrows(IllegalStateException.class, () -> wave.markReady(T0.plusSeconds(6)));

        wave.authorizeDepartureException("Supervisor approved mis-sort quarantine", T0.plusSeconds(7));
        wave.markReady(T0.plusSeconds(8));
        assertEquals(DispatchWaveStatus.READY, wave.status());
        assertEquals("Supervisor approved mis-sort quarantine", wave.authorizedExceptionReason());
        assertTrue(wave.unexpectedParcels().contains("P-X"));
        assertTrue(wave.duplicateScans().contains("P-1"));
    }

    @Test
    void rejectsEmptyWaveSameHubAndBackwardsTime() {
        assertThrows(IllegalArgumentException.class, () -> DispatchWave.plan(
                UUID.randomUUID(), TenantId.generate(), "WAVE-1", "HUB-A", "HUB-A", T0));
        DispatchWave wave = wave();
        assertThrows(IllegalStateException.class, () -> wave.beginLoading(T0.plusSeconds(1)));
        wave.expectParcel("P-1", T0.plusSeconds(2));
        assertThrows(IllegalArgumentException.class, () -> wave.beginLoading(T0.plusSeconds(1)));
        assertEquals(T0.plusSeconds(2), wave.updatedAt());
    }

    @Test
    void departedWaveCannotBeCancelled() {
        DispatchWave wave = wave();
        wave.expectParcel("P-1", T0.plusSeconds(1));
        wave.beginLoading(T0.plusSeconds(2));
        wave.scanParcel("P-1", T0.plusSeconds(3));
        wave.markReady(T0.plusSeconds(4));
        wave.depart(T0.plusSeconds(5));
        assertThrows(IllegalStateException.class, () -> wave.cancel(T0.plusSeconds(6)));
    }

    private static DispatchWave ready(DispatchWave wave, Instant at) {
        wave.markReady(at);
        return wave;
    }

    private static DispatchWave wave() {
        return DispatchWave.plan(UUID.randomUUID(), TenantId.generate(), "WAVE-100", "HUB-BDG", "HUB-JKT", T0);
    }
}
