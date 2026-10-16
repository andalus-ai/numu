package tech.kayys.numou.asset.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.domain.document.DocumentReference;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Adapter for the narrow Documents existence-check port (ASSET-24 §18).
 *
 * <p>Deliberately a <strong>seam</strong>: the Asset bounded context must not
 * own document metadata or storage, so this default implementation only
 * validates a syntactically usable document reference and always reports the
 * document as existing. A deployment that runs alongside the reusable
 * Documents capability must replace this bean with the real Documents-backed
 * adapter (which verifies {@code documentId} belongs to {@code tenantId}).</p>
 *
 * <p>Kept intentionally simple so the module is deployable standalone without
 * pretending Asset owns documents.</p>
 */
@ApplicationScoped
public class DocumentReferenceAdapter implements DocumentReference {

    @Override
    public CompletionStage<Boolean> exists(String tenantId, UUID documentId) {
        boolean present = tenantId != null && !tenantId.isBlank() && documentId != null;
        return java.util.concurrent.CompletableFuture.completedFuture(present);
    }
}