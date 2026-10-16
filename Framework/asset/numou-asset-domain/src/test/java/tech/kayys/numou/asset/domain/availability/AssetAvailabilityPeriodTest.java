package tech.kayys.syirkah.asset.domain.availability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Temporal-boundary tests for availability periods (ASSET-26 acceptance:
 * "open-ended unavailability", "temporal overlap detection", interval validity).
 */
@DisplayName("Asset availability period")
class AssetAvailabilityPeriodTest {

    private static final Instant T08 = Instant.parse("2026-10-03T08:00:00Z");
    private static final Instant T10 = Instant.parse("2026-10-03T10:00:00Z");
    private static final Instant T12 = Instant.parse("2026-10-03T12:00:00Z");
    private static final Instant T17 = Instant.parse("2026-10-03T17:00:00Z");

    private static AssetAvailabilityPeriod period(
            Instant start, Instant end, AssetAvailabilityType type) {
        return AssetAvailabilityPeriod.mark(
                AssetAvailabilityPeriodId.generate(), "tenant-a", UUID.randomUUID(),
                start, end, type, AssetAvailabilityReason.NORMAL_OPERATION, null, null);
    }

    @Test
    @DisplayName("an open-ended period has no end and is open")
    void supportsOpenEnded() {
        AssetAvailabilityPeriod open = period(T10, null, AssetAvailabilityType.UNAVAILABLE);
        assertTrue(open.isOpen());
        assertTrue(open.isUnavailable());
        assertTrue(open.covers(Instant.parse("2030-01-01T00:00:00Z")));
    }

    @Test
    @DisplayName("endsAt must be strictly after startsAt")
    void rejectsInvertedInterval() {
        assertThrows(BusinessRuleViolation.class,
                () -> period(T12, T10, AssetAvailabilityType.AVAILABLE));
    }

    @Test
    @DisplayName("covers is inclusive of start and exclusive of end")
    void coversWindow() {
        AssetAvailabilityPeriod window = period(T08, T17, AssetAvailabilityType.AVAILABLE);
        assertTrue(window.covers(T08));
        assertTrue(window.covers(T12));
        assertFalse(window.covers(T17));
        assertFalse(window.covers(Instant.parse("2026-10-03T07:59:59Z")));
    }

    @Test
    @DisplayName("static overlap treats a null end as unbounded")
    void detectsOverlap() {
        // existing 08:00-17:00 vs proposed 10:00-12:00 -> overlap
        assertTrue(AssetAvailabilityPeriod.overlaps(T08, T17, T10, T12));
        // existing open-ended from 10:00 vs proposed 12:00-14:00 -> overlap
        assertTrue(AssetAvailabilityPeriod.overlaps(T10, null, T12, Instant.parse("2026-10-03T14:00:00Z")));
        // adjacent windows 08:00-10:00 and 10:00-12:00 do NOT overlap (half-open)
        assertFalse(AssetAvailabilityPeriod.overlaps(T08, T10, T10, T12));
    }

    @Test
    @DisplayName("instance overlap uses the period's own window")
    void instanceOverlap() {
        AssetAvailabilityPeriod window = period(T08, T17, AssetAvailabilityType.AVAILABLE);
        assertTrue(window.overlaps(T10, T12));
        assertFalse(window.overlaps(T17, Instant.parse("2026-10-03T18:00:00Z")));
    }

    @Test
    @DisplayName("a null reason defaults to OTHER")
    void defaultsReason() {
        AssetAvailabilityPeriod period = AssetAvailabilityPeriod.mark(
                AssetAvailabilityPeriodId.generate(), "tenant-a", UUID.randomUUID(),
                T08, T17, AssetAvailabilityType.AVAILABLE, null, null, null);
        assertEquals(AssetAvailabilityReason.OTHER, period.reason());
    }
}