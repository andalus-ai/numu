package tech.kayys.numou.history.source.spi;

import tech.kayys.numou.history.source.domain.SourceExtractionArtifact;
import tech.kayys.numou.history.source.domain.SourceIngestionRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped persistence contract for immutable ingestion and extraction provenance. */
public interface SourceIngestionRepository {
    boolean appendIngestion(UUID tenantId, SourceIngestionRequest request);

    Optional<SourceIngestionRequest> findIngestion(UUID tenantId, UUID ingestionId);

    List<SourceIngestionRequest> listIngestions(UUID tenantId, UUID sourceId, int limit);

    boolean appendExtraction(UUID tenantId, SourceExtractionArtifact artifact);

    List<SourceExtractionArtifact> listExtractions(UUID tenantId, UUID ingestionId, int limit);
}
