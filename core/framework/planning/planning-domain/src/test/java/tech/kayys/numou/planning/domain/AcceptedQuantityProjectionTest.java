package tech.kayys.numou.planning.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AcceptedQuantityProjectionTest {
    @Test void pendingCorrectionDoesNotReplaceAcceptedEvidenceButApprovalDoes() {
        UUID tenant = UUID.randomUUID(), definition = UUID.randomUUID();
        var original = measurement(tenant, definition, "3", "original", "2026-10-20");
        var replacement = measurement(tenant, definition, "4", "replacement", "2026-10-21");
        var correction = correction(tenant, definition, original, replacement, "c1");
        var pending = AcceptedQuantityProjection.resolve(tenant, definition, List.of(original, replacement),
                List.of(correction), List.of());
        assertEquals(original.id(), pending.acceptedMeasurementId());
        assertEquals(new BigDecimal("3"), pending.acceptedQuantity());
        assertEquals(List.of(correction.id()), pending.pendingCorrectionIds());

        var approval = new QuantityCorrectionDecision(UUID.randomUUID(), tenant, definition, correction.id(),
                QuantityCorrectionDecision.Decision.APPROVED, Instant.parse("2026-10-22T10:00:00Z"),
                "reviewer", "Evidence verified", "decision-1");
        var accepted = AcceptedQuantityProjection.resolve(tenant, definition, List.of(original, replacement),
                List.of(correction), List.of(approval));
        assertEquals(replacement.id(), accepted.acceptedMeasurementId());
        assertEquals(new BigDecimal("4"), accepted.acceptedQuantity());
        assertEquals(List.of(correction.id()), accepted.appliedCorrectionIds());
    }

    @Test void rejectedCorrectionNeverChangesAcceptedQuantity() {
        UUID tenant = UUID.randomUUID(), definition = UUID.randomUUID();
        var original = measurement(tenant, definition, "3", "original", "2026-10-20");
        var replacement = measurement(tenant, definition, "9", "replacement", "2026-10-21");
        var correction = correction(tenant, definition, original, replacement, "c1");
        var rejection = new QuantityCorrectionDecision(UUID.randomUUID(), tenant, definition, correction.id(),
                QuantityCorrectionDecision.Decision.REJECTED, Instant.parse("2026-10-22T10:00:00Z"),
                "reviewer", "Source is invalid", "decision-2");
        var projection = AcceptedQuantityProjection.resolve(tenant, definition, List.of(original, replacement),
                List.of(correction), List.of(rejection));
        assertEquals(original.id(), projection.acceptedMeasurementId());
        assertEquals(new BigDecimal("3"), projection.acceptedQuantity());
        assertEquals(List.of(correction.id()), projection.rejectedCorrectionIds());
    }

    @Test void rejectsCrossTenantEvidence() {
        UUID tenant = UUID.randomUUID(), definition = UUID.randomUUID();
        var wrongTenant = measurement(UUID.randomUUID(), definition, "1", "wrong", "2026-10-20");
        assertThrows(IllegalArgumentException.class, () -> AcceptedQuantityProjection.resolve(
                tenant, definition, List.of(wrongTenant), List.of(), List.of()));
    }

    private QuantityMeasurement measurement(UUID tenant, UUID definition, String quantity, String key, String date) {
        return new QuantityMeasurement(UUID.randomUUID(), tenant, definition, LocalDate.parse(date),
                new BigDecimal(quantity), Instant.parse(date + "T10:00:00Z"), "surveyor", key, null);
    }
    private QuantityMeasurementCorrection correction(UUID tenant, UUID definition, QuantityMeasurement original,
            QuantityMeasurement replacement, String key) {
        return new QuantityMeasurementCorrection(UUID.randomUUID(), tenant, definition, original.id(), replacement.id(),
                Instant.parse("2026-10-22T09:00:00Z"), "auditor", "Correction verified", key);
    }
}
