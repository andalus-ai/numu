package tech.kayys.numou.history.source.spi;

import tech.kayys.numou.history.source.domain.HistoricalSourceId;
import tech.kayys.numou.history.source.domain.SourceExtractionArtifact;
import tech.kayys.numou.history.source.domain.SourceIngestionRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped append-only persistence contract for ingestion requests and extraction provenance. */
public interface SourceIngestionRepository {
    /** Returns the existing request for a repeated tenant/source/idempotency key, if any. */
    Optional<SourceIngestionRequest> findRequestByIdempotencyKey(
        UUID tenantId, HistoricalSourceId sourceId, String idempotencyKey
    );

    /** Atomically inserts a request; false means the unique idempotency key already exists. */
    boolean appendRequest(UUID tenantId, SourceIngestionRequest request);

    Optional<SourceIngestionRequest> findRequest(UUID tenantId, UUID requestId);

    /** Appends a completed attempt. Implementations must enforce unique (request, attempt). */
    boolean appendExtraction(UUID tenantId, SourceExtractionArtifact artifact);

    List<SourceExtractionArtifact> listExtractions(UUID tenantId, UUID requestId, int limit);
}
