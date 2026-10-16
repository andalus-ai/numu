package tech.kayys.syirkah.asset.domain.document;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Narrow existence-check port into the Documents capability (ASSET-24 §18).
 *
 * <p>The Asset context must NOT access any {@code DocumentRepository}
 * directly — only this port. Tenant matching is the caller's duty: pass the
 * asset's tenant so a cross-tenant document can never validate.</p>
 */
public interface DocumentReference {

    CompletionStage<Boolean> exists(String tenantId, UUID documentId);
}
