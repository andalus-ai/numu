package tech.kayys.numou.history.source.application;

import tech.kayys.numou.history.source.domain.HistoricalSourceId;

import java.util.UUID;

/** Host policy for creating ingestion work and reading its derived artifacts. */
public interface SourceIngestionAuthorizationPort {
    void requireCanIngestSource(UUID tenantId, HistoricalSourceId sourceId, String requestedBy);
    void requireCanReadSourceIngestion(UUID tenantId, HistoricalSourceId sourceId);
}
