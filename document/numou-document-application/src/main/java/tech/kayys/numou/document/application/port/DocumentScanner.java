package tech.kayys.numou.document.application.port;

import io.smallrye.mutiny.Uni;

import java.time.Instant;
import java.util.Objects;

/**
 * Provider-neutral malware scanning boundary. Implementations must inspect the
 * exact immutable object version and must not infer CLEAN from upload success.
 */
public interface DocumentScanner {
    Uni<ScanVerdict> scan(ScanRequest request);

    record ScanRequest(
            String tenantId,
            String documentId,
            String documentVersionId,
            String storageKey,
            String sha256
    ) {
        public ScanRequest {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(documentVersionId, "documentVersionId");
            Objects.requireNonNull(storageKey, "storageKey");
            Objects.requireNonNull(sha256, "sha256");
            if (tenantId.isBlank() || documentId.isBlank() || documentVersionId.isBlank()
                    || storageKey.isBlank() || !sha256.matches("(?i)[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid document scan request");
            }
        }
    }

    record ScanVerdict(
            Outcome outcome,
            String scanner,
            String signatureVersion,
            String failureCode,
            Instant scannedAt
    ) {
        public ScanVerdict {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(scannedAt, "scannedAt");
            if (scanner == null || scanner.isBlank() || scanner.length() > 200) {
                throw new IllegalArgumentException("scanner must contain 1 to 200 characters");
            }
            if (signatureVersion != null && signatureVersion.length() > 200) {
                throw new IllegalArgumentException("signatureVersion must not exceed 200 characters");
            }
            if (outcome == Outcome.FAILED && (failureCode == null || failureCode.isBlank()
                    || failureCode.length() > 200)) {
                throw new IllegalArgumentException("FAILED verdicts require a failureCode of at most 200 characters");
            }
            if (outcome != Outcome.FAILED && failureCode != null) {
                throw new IllegalArgumentException("Only FAILED verdicts may contain a failureCode");
            }
        }
    }

    enum Outcome {
        CLEAN,
        INFECTED,
        FAILED
    }
}
