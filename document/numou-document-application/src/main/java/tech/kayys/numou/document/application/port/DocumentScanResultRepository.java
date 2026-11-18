package tech.kayys.numou.document.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.accounting.domain.document.DocumentScanResult;

/**
 * Persists the authoritative verdict for an immutable document version.
 * Implementations must be tenant-scoped and reject replacement of CLEAN/INFECTED
 * terminal decisions. FAILED may be replaced by a later successful retry.
 */
public interface DocumentScanResultRepository {
    /**
     * @return true only when a new verdict was recorded; false for an idempotent
     *         replay or when a terminal decision already exists.
     */
    Uni<Boolean> record(String tenantId, DocumentScanResult result);
}
