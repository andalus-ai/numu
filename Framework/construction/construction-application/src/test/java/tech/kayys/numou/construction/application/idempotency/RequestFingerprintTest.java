package tech.kayys.numou.construction.application.idempotency;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestFingerprintTest {
    @Test void sameCanonicalPayloadHasSameFingerprint() {
        assertEquals(RequestFingerprint.sha256("{\"a\":1}"),
                RequestFingerprint.sha256("{\"a\":1}"));
    }

    @Test void differentPayloadsHaveDifferentFingerprints() {
        assertNotEquals(RequestFingerprint.sha256("{\"a\":1}"),
                RequestFingerprint.sha256("{\"a\":2}"));
    }

    @Test void rejectsNullPayload() {
        assertThrows(NullPointerException.class, () -> RequestFingerprint.sha256(null));
    }
}
