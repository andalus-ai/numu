package tech.kayys.numou.feedback.application.port.out;

import java.io.IOException;
import java.io.InputStream;

/** Adapter to Numou Document capability; Feedback never owns provider-specific object keys. */
public interface FeedbackEvidenceStore {
    StoredEvidence store(Upload upload) throws IOException;
    void delete(String documentReference) throws IOException;

    record Upload(String tenantId, String fileName, String mediaType, long sizeBytes,
                  String sha256, InputStream content) {
        public Upload {
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
            if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName is required");
            if (mediaType == null || mediaType.isBlank()) throw new IllegalArgumentException("mediaType is required");
            if (sizeBytes <= 0) throw new IllegalArgumentException("sizeBytes must be positive");
            if (sha256 == null || !sha256.matches("(?i)[a-f0-9]{64}")) throw new IllegalArgumentException("sha256 is invalid");
            if (content == null) throw new IllegalArgumentException("content is required");
        }
    }
    record StoredEvidence(String documentReference, long sizeBytes, String mediaType, String sha256) {
        public StoredEvidence {
            if (documentReference == null || documentReference.isBlank()) throw new IllegalArgumentException("documentReference is required");
            if (sizeBytes <= 0) throw new IllegalArgumentException("sizeBytes must be positive");
            if (mediaType == null || mediaType.isBlank()) throw new IllegalArgumentException("mediaType is required");
            if (sha256 == null || !sha256.matches("(?i)[a-f0-9]{64}")) throw new IllegalArgumentException("sha256 is invalid");
        }
    }
}