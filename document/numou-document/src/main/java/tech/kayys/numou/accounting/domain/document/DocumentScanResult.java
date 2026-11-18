package tech.kayys.numou.accounting.domain.document;

import java.time.Instant;
import java.util.Objects;

/**
 * Authoritative malware-scan result for one immutable document version.
 * FAILED is an operational failure, never an approval to serve the document.
 */
public record DocumentScanResult(
        DocumentId documentId,
        DocumentVersionId versionId,
        DocumentScanStatus status,
        String scanner,
        String signatureVersion,
        String failureCode,
        Instant scannedAt
) {
    public DocumentScanResult {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(scannedAt, "scannedAt");
        if (scanner == null || scanner.isBlank() || scanner.length() > 200) {
            throw new IllegalArgumentException("scanner must contain 1 to 200 characters");
        }
        if (signatureVersion != null && signatureVersion.length() > 200) {
            throw new IllegalArgumentException("signatureVersion must not exceed 200 characters");
        }
        if (status == DocumentScanStatus.PENDING) {
            throw new IllegalArgumentException("A persisted scan result cannot remain PENDING");
        }
        if (status == DocumentScanStatus.FAILED && (failureCode == null || failureCode.isBlank()
                || failureCode.length() > 200)) {
            throw new IllegalArgumentException("FAILED scan results require a failureCode of at most 200 characters");
        }
        if (status != DocumentScanStatus.FAILED && failureCode != null) {
            throw new IllegalArgumentException("Only FAILED scan results may contain a failureCode");
        }
    }

    public boolean isSafeToServe() {
        return status == DocumentScanStatus.CLEAN;
    }
}
