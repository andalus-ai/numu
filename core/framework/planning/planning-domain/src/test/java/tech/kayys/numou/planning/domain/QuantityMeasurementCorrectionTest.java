package tech.kayys.numou.planning.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QuantityMeasurementCorrectionTest {
    @Test
    void correctionRequiresDistinctEvidenceAndAnExplicitReason() {
        UUID original = UUID.randomUUID();
        UUID replacement = UUID.randomUUID();
        var correction = new QuantityMeasurementCorrection(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), original, replacement,
                Instant.parse("2026-10-23T10:00:00Z"), "reviewer", "Correct transcription error", "correction-1");
        assertEquals(original, correction.originalMeasurementId());
        assertEquals(replacement, correction.replacementMeasurementId());
        assertThrows(IllegalArgumentException.class, () -> new QuantityMeasurementCorrection(
                UUID.randomUUID(), correction.tenantId(), correction.definitionId(), original, original,
                Instant.now(), "reviewer", "Correction", "correction-2"));
        assertThrows(IllegalArgumentException.class, () -> new QuantityMeasurementCorrection(
                UUID.randomUUID(), correction.tenantId(), correction.definitionId(), original, replacement,
                Instant.now(), "reviewer", " ", "correction-3"));
    }
}
