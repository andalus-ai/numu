package tech.kayys.numou.history.source.application;

import java.util.UUID;

/** Host-owned authorization for source ingestion requests and trusted extraction workers. */
public interface SourceIngestionAuthorizationPort {
    void requireCanRequestIngestion(UUID tenantId, UUID sourceId, String actorRef);

    void requireCanReadIngestions(UUID tenantId, UUID sourceId);

    void requireCanRecordExtraction(UUID tenantId, UUID ingestionId, String workerRef);

    void requireCanReadExtractions(UUID tenantId, UUID ingestionId);
}
