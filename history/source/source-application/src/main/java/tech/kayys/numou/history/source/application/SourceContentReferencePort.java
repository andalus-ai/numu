package tech.kayys.numou.history.source.application;

import java.util.UUID;

/**
 * Host adapter to the shared document capability. It must verify tenant ownership,
 * version existence and permission to reference the immutable document version.
 */
@FunctionalInterface
public interface SourceContentReferencePort {
    void requireCanReferenceVersion(UUID tenantId, UUID documentId, UUID documentVersionId);
}
